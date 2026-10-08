"""Train a fixed three-member MobileNetV3 ensemble, validate, then install its bundle."""
import argparse
import gc
import hashlib
import json
import platform
import shutil
import statistics
import tempfile
import time
from datetime import datetime, timezone
from importlib.metadata import distributions, version
from pathlib import Path

import numpy as np

from leafcare.common import contract, load_config, location, read_json, sha256, write_json
from leafcare.dataset import load_manifest
from leafcare.ensemble import build_ensemble, calibrated_mean
from leafcare.preprocessing import preprocess
from experiments.dinov2.benchmark import (
    classification_metrics, encode_labels, fit_temperature, select_threshold, split_rows, validate_manifest,
)

ROOT = Path(__file__).resolve().parent
OUTPUT = ROOT / "benchmark_artifacts/ensemble"
REPORTS = ROOT.parent / "docs/model-reports/ensemble"
CACHE = ROOT / "experiments/ensemble/.cache"
# Composition fixed from the historical validation winner, before new training.
MEMBERS = [
    {"id": "small_adam", "architecture": "MobileNetV3Small", "seed": 42,
     "dropout": .25, "optimizer": "Adam", "learning_rate": .001,
     "fine_tune_last_layers": 30, "epochs_frozen": 25, "epochs_finetune": 20, "patience": 6},
    {"id": "small_rmsprop", "architecture": "MobileNetV3Small", "seed": 54,
     "dropout": .25, "optimizer": "RMSprop", "momentum": .9, "learning_rate": .0007,
     "fine_tune_last_layers": 30, "epochs_frozen": 12, "epochs_finetune": 12, "patience": 3},
    {"id": "large_adamw", "architecture": "MobileNetV3Large", "seed": 44,
     "dropout": .30, "optimizer": "AdamW", "weight_decay": 1e-5, "learning_rate": .0007,
     "fine_tune_last_layers": 40, "epochs_frozen": 12, "epochs_finetune": 12, "patience": 3},
]


def initialize_tensorflow():
    import tensorflow as tf
    tf.config.threading.set_inter_op_parallelism_threads(2)
    tf.config.threading.set_intra_op_parallelism_threads(6)
    for gpu in tf.config.list_physical_devices("GPU"):
        tf.config.experimental.set_memory_growth(gpu, True)
    tf.config.experimental.enable_op_determinism()
    return tf


def member_path(spec):
    return CACHE / (spec["id"] + ".keras")


def predict(model, config, manifest, split):
    from leafcare.training import make_dataset
    return model.predict(make_dataset(config, manifest, split), verbose=0)


def train_member(spec, config, manifest):
    import tensorflow as tf
    from leafcare.training import class_weights, enable_finetuning, make_dataset
    folder = OUTPUT / spec["id"]
    folder.mkdir(parents=True, exist_ok=True)
    selected = member_path(spec)
    if selected.exists():
        recorded = read_json(folder / "training.json")
        if recorded["recipe"] != spec or recorded["keras_sha256"] != sha256(selected):
            raise ValueError("Existing member does not match its recorded training")
        if recorded["manifest_sha256"] != sha256(location(config, "prepared_dir") / "manifest.json"):
            raise ValueError("Existing member belongs to another dataset/split")
        print(f"Verified previously completed member: {spec['id']}", flush=True)
        return
    tf.keras.backend.clear_session()
    tf.keras.utils.set_random_seed(spec["seed"])
    run_config = {**config, "seed": spec["seed"]}
    train_ds = make_dataset(run_config, manifest, "train", augment=True)
    validation_ds = make_dataset(run_config, manifest, "validation")
    backbone = getattr(tf.keras.applications, spec["architecture"])(
        input_shape=(224, 224, 3), include_top=False, include_preprocessing=True,
        weights="imagenet", pooling="avg")
    pretrained_hash = hashlib.sha256(b"".join(w.tobytes() for w in backbone.get_weights())).hexdigest()
    backbone.trainable = False
    inputs = tf.keras.Input((224, 224, 3), name="rgb_0_255")
    features = tf.keras.layers.Dropout(spec["dropout"])(backbone(inputs, training=False))
    scores = tf.keras.layers.Dense(len(manifest["classes"]), activation="softmax", name="probabilities")(features)
    model = tf.keras.Model(inputs, scores, name="leafcare_" + spec["id"])
    weights = class_weights(manifest, config["class_weight_ratio_threshold"])
    history, losses = {}, {}
    started = time.perf_counter()
    for stage in ("frozen", "finetune"):
        if stage == "finetune":
            enable_finetuning(backbone, spec["fine_tune_last_layers"])
        lr = spec["learning_rate"] if stage == "frozen" else 1e-5
        optimizer_type = getattr(tf.keras.optimizers, spec["optimizer"])
        extra = {key: spec[key] for key in ("momentum", "weight_decay") if key in spec}
        model.compile(optimizer=optimizer_type(learning_rate=lr, **extra),
                      loss="sparse_categorical_crossentropy", metrics=["accuracy"])
        checkpoint = CACHE / f"{spec['id']}_{stage}.weights.h5"
        callbacks = [
            tf.keras.callbacks.ModelCheckpoint(str(checkpoint), monitor="val_loss", save_best_only=True, save_weights_only=True),
            tf.keras.callbacks.EarlyStopping(monitor="val_loss", patience=spec["patience"], restore_best_weights=True),
            tf.keras.callbacks.ReduceLROnPlateau(monitor="val_loss", patience=3 if spec["id"] == "small_adam" else 2,
                                               factor=.5 if spec["id"] == "small_adam" else .35, min_lr=1e-7),
            tf.keras.callbacks.TerminateOnNaN(),
        ]
        print(f"Training {spec['id']}: {stage}", flush=True)
        result = model.fit(train_ds, validation_data=validation_ds, epochs=spec["epochs_" + stage],
                           class_weight=weights, callbacks=callbacks, verbose=2)
        if not all(np.isfinite(result.history["loss"])):
            raise ValueError("Non-finite training loss")
        model.load_weights(checkpoint)
        losses[stage] = float(model.evaluate(validation_ds, verbose=0)[0])
        history[stage] = {key: [float(v) for v in values] for key, values in result.history.items()}
        write_json(folder / "history.json", history)
    stage = "finetune" if losses["finetune"] < losses["frozen"] else "frozen"
    model.load_weights(CACHE / f"{spec['id']}_{stage}.weights.h5")
    model.save(selected)
    probabilities = predict(model, config, manifest, "validation")
    truth = encode_labels(split_rows(manifest, "validation"), manifest["classes"])
    metrics = classification_metrics(truth, probabilities, manifest["classes"])
    np.save(folder / "validation_probabilities.npy", probabilities)
    write_json(folder / "validation_metrics.json", metrics)
    write_json(folder / "training.json", {
        "recipe": spec, "selected_stage": stage, "validation_loss": losses,
        "class_weights": weights, "parameters": model.count_params(),
        "seconds": time.perf_counter() - started, "keras_sha256": sha256(selected),
        "imagenet_weights_sha256": pretrained_hash,
        "manifest_sha256": sha256(location(config, "prepared_dir") / "manifest.json"),
        "test_used_for_selection": False, "trained_at": datetime.now(timezone.utc).isoformat(),
    })
    print(json.dumps({"member": spec["id"], "stage": stage, "validation_top1": metrics["top1_accuracy"],
                      "validation_macro_f1": metrics["macro_f1"]}), flush=True)
    del model, backbone, train_ds, validation_ds
    tf.keras.backend.clear_session()
    gc.collect()


def save_metrics(path, truth, probabilities, classes, threshold, split, model_hash):
    metrics = classification_metrics(truth, probabilities, classes)
    accepted = probabilities.max(axis=1) >= np.float32(threshold)
    correct = probabilities.argmax(axis=1) == truth
    metrics.update(status="evaluated", classes=classes, model_sha256=model_hash, inference_backend="TFLite float32",
                   accuracy=metrics["top1_accuracy"], split=split, threshold=float(np.float32(threshold)),
                   threshold_calibrated=True, coverage=float(accepted.mean()), accepted=int(accepted.sum()),
                   accepted_correct=int((accepted & correct).sum()),
                   accepted_accuracy=float(correct[accepted].mean()) if accepted.any() else None,
                   test_used_for_selection=False)
    write_json(path, metrics)
    return metrics


def save_diagnostics(output, classes, matrix):
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    for metric, title in (("loss", "Perda"), ("accuracy", "Acurácia")):
        fig, axes = plt.subplots(1, 3, figsize=(14, 4))
        for ax, spec in zip(axes, MEMBERS):
            history = read_json(OUTPUT / spec["id"] / "history.json")
            for prefix, label in (("", "Treino"), ("val_", "Validação")):
                values = history["frozen"][prefix + metric] + history["finetune"][prefix + metric]
                ax.plot(range(1, len(values) + 1), values, label=label)
            ax.axvline(len(history["frozen"][metric]) + .5, color="gray", linestyle="--")
            ax.set(title=spec["id"], xlabel="Época", ylabel=title)
            ax.legend()
        fig.tight_layout()
        fig.savefig(output / (metric + ".png"), dpi=160)
        plt.close(fig)
    fig, ax = plt.subplots(figsize=(11, 9))
    plot = ax.imshow(matrix, cmap="Greens")
    ax.set(xticks=range(len(classes)), yticks=range(len(classes)), xticklabels=classes, yticklabels=classes,
           xlabel="Classe prevista", ylabel="Classe verdadeira", title="LeafCare — ensemble, teste")
    plt.setp(ax.get_xticklabels(), rotation=60, ha="right", fontsize=8)
    for (i, j), value in np.ndenumerate(matrix):
        ax.text(j, i, str(value), ha="center", va="center", fontsize=8)
    fig.colorbar(plot, ax=ax)
    fig.tight_layout()
    fig.savefig(output / "confusion_matrix.png", dpi=160)
    plt.close(fig)


def export(config, manifest):
    import tensorflow as tf
    from export_tflite import convert_model
    from leafcare.inference import TFLitePredictor
    classes = manifest["classes"]
    recordings = [read_json(OUTPUT / s["id"] / "training.json") for s in MEMBERS]
    for spec, recording in zip(MEMBERS, recordings):
        if recording["recipe"] != spec or recording["keras_sha256"] != sha256(member_path(spec)):
            raise ValueError("Member changed after training")
        if recording["manifest_sha256"] != sha256(location(config, "prepared_dir") / "manifest.json"):
            raise ValueError("Dataset/split changed after training")
    models = [tf.keras.models.load_model(member_path(s), compile=False) for s in MEMBERS]
    validation_parts = [predict(model, config, manifest, "validation") for model in models]
    for spec, probabilities in zip(MEMBERS, validation_parts):
        np.testing.assert_allclose(probabilities, np.load(OUTPUT / spec["id"] / "validation_probabilities.npy"), atol=1e-5, rtol=0)
    truth = encode_labels(split_rows(manifest, "validation"), classes)
    mean = np.mean(validation_parts, axis=0)
    temperature, calibration = fit_temperature(truth, np.log(np.clip(mean, 1e-7, 1)))
    calibrated = calibrated_mean(validation_parts, temperature)
    threshold, threshold_selection = select_threshold(truth, calibrated)
    threshold = float(np.float32(threshold))
    ensemble = build_ensemble(models, temperature)
    ensemble.save(CACHE / "ensemble.keras")
    configuration = {
        "architecture": "MobileNetV3Ensemble", "composition_fixed_before_training": True,
        "members": recordings, "temperature": temperature, "calibration": calibration,
        "threshold": threshold, "threshold_selection": threshold_selection,
        "manifest_sha256": sha256(location(config, "prepared_dir") / "manifest.json"),
        "class_order": classes, "parameters": ensemble.count_params(),
        "keras_sha256": sha256(CACHE / "ensemble.keras"), "test_used_for_selection": False,
        "selection_note": "Fixed historical winning composition, new ImageNet transfer training; validation-only checkpoint/calibration selection",
    }
    # Freeze calibration and member identities before any test inference.
    write_json(OUTPUT / "experiment_config.json", configuration)
    with tempfile.TemporaryDirectory() as temporary:
        candidate = Path(temporary) / "leafcare.tflite"
        candidate.write_bytes(convert_model(ensemble, temporary))
        predictor = TFLitePredictor(candidate, classes)
        rows = split_rows(manifest, "validation")
        original, converted = [], []
        for row in rows:
            tensor = preprocess(location(config, "dataset_dir") / row["path"])
            original.append(ensemble(tensor, training=False).numpy()[0])
            converted.append(predictor.scores(tensor)[0])
        original, converted = np.asarray(original), np.asarray(converted)
        agreement = float((original.argmax(axis=1) == converted.argmax(axis=1)).mean())
        max_error = float(np.abs(original - converted).max())
        formula_error = float(np.abs(original - calibrated).max())
        parity = {"split": "validation", "n_images": len(rows), "top1_agreement": agreement,
                  "max_abs_error": max_error, "numpy_formula_max_abs_error": formula_error,
                  "max_abs_error_limit": config["export"]["parity_max_abs_error"]}
        parity["passed"] = agreement == 1 and max(max_error, formula_error) <= parity["max_abs_error_limit"]
        write_json(OUTPUT / "conversion_parity.json", parity)
        np.savez_compressed(OUTPUT / "conversion_probabilities.npz", keras=original, tflite=converted)
        if not parity["passed"]:
            raise ValueError("Ensemble conversion parity failed; app assets were not replaced")
        # Use the deployed runtime's validation scores for the final boundary.
        threshold, threshold_selection = select_threshold(truth, converted)
        below = converted.max(axis=1)[converted.max(axis=1) < threshold]
        if len(below):
            threshold = (float(below.max()) + threshold) / 2
        threshold = float(np.float32(threshold))
        configuration.update(threshold=threshold, threshold_selection={**threshold_selection,
            "runtime": "TFLite float32", "boundary": "midpoint between rejected and accepted confidence", "applied_threshold": threshold})
        write_json(OUTPUT / "experiment_config.json", configuration)
        model_hash = sha256(candidate)
        validation = save_metrics(OUTPUT / "validation_metrics.json", truth, converted, classes, threshold, "validation", model_hash)
        test_rows = split_rows(manifest, "test")
        test_truth = encode_labels(test_rows, classes)
        test_probabilities = np.asarray([predictor.scores(preprocess(location(config, "dataset_dir") / row["path"]))[0] for row in test_rows])
        test = save_metrics(OUTPUT / "test_metrics.json", test_truth, test_probabilities, classes, threshold, "test", model_hash)
        write_json(OUTPUT / "predictions_test.json", [
            {"path": row["path"], "true_class": row["class_id"], "probabilities": scores.tolist()}
            for row, scores in zip(test_rows, test_probabilities)])
        np.savez_compressed(OUTPUT / "probabilities.npz", validation=converted, test=test_probabilities)
        inference, pipeline = [], []
        tensor = preprocess(location(config, "dataset_dir") / rows[0]["path"])
        for i in range(35):
            _, elapsed = predictor.scores(tensor)
            started = time.perf_counter()
            scores, _ = predictor.scores(preprocess(location(config, "dataset_dir") / rows[i % len(rows)]["path"]))
            np.argsort(-scores, kind="stable")[:3]
            duration = (time.perf_counter() - started) * 1000
            if i >= 5:
                inference.append(elapsed)
                pipeline.append(duration)
        timing = {"desktop_median_ms": statistics.median(inference),
                  "desktop_p95_ms": float(np.percentile(inference, 95)),
                  "desktop_pipeline_median_ms": statistics.median(pipeline),
                  "desktop_pipeline_p95_ms": float(np.percentile(pipeline, 95)),
                  "threads": 2, "warmups": 5, "samples": 30,
                  "device": "Intel Core i5-13400 desktop CPU", "android_reference_device": "Samsung Galaxy A06",
                  "android_full_analysis_ms": None, "android_target_ms": 3000, "android_target_verified": False,
                  "note": "Measured complete fused ensemble. Python pipeline includes decode/preprocessing/Top-3, excludes Android Room/UI."}
        write_json(OUTPUT / "timing.json", timing)
        reference = ROOT.parent / "samples/reference_frog_eye.jpg"
        reference_scores, _ = predictor.scores(preprocess(reference))
        write_json(OUTPUT / "reference_prediction.json", {
            "image": str(reference.relative_to(ROOT.parent)), "image_sha256": sha256(reference),
            "probabilities": reference_scores.tolist(), "class_order": classes,
        })
        metadata = contract(classes, threshold)
        metadata.update(architecture="MobileNetV3Ensemble", threshold_calibrated=True,
                        aggregation="mean_probabilities", temperature=temperature,
                        probability_calibration="embedded_temperature_scaling", member_count=3,
                        members=[{"id": s["id"], "architecture": s["architecture"],
                                  "keras_sha256": r["keras_sha256"]} for s, r in zip(MEMBERS, recordings)],
                        seed=config["seed"], tensorflow=tf.__version__, keras=version("keras"),
                        trained_at=datetime.now(timezone.utc).isoformat(), parameters=ensemble.count_params(),
                        keras_sha256=sha256(CACHE / "ensemble.keras"), manifest_sha256=configuration["manifest_sha256"],
                        model_sha256=sha256(candidate), conversion_parity=parity, test_used_for_selection=False)
        # Installation happens only after real TFLite validation succeeds.
        output = location(config, "output_dir")
        assets = (config["_base"] / config["export"]["android_assets_dir"]).resolve()
        shutil.copy2(candidate, output / "leafcare.tflite")
        shutil.copy2(candidate, assets / "leafcare.tflite")
        shutil.copy2(CACHE / "ensemble.keras", output / "model.keras")
        for folder in (output, assets):
            write_json(folder / "model_metadata.json", metadata)
        write_json(output / "training_metadata.json", metadata)
        write_json(output / "metrics.json", test)
        write_json(REPORTS / "history.json", {s["id"]: read_json(OUTPUT / s["id"] / "history.json") for s in MEMBERS})
        write_json(REPORTS / "config_used.json", {"pipeline": "train_ensemble.py", "base_config": {k: v for k, v in config.items() if not k.startswith("_")}, "ensemble": configuration})
        write_json(REPORTS / "conversion_parity.json", parity)
        write_json(REPORTS / "test_predictions.json", read_json(OUTPUT / "predictions_test.json"))
        write_json(REPORTS / "confusion_matrix.json", {"classes": classes, "matrix": test["confusion_matrix"]})
        save_diagnostics(REPORTS, classes, np.asarray(test["confusion_matrix"]))
        write_json(OUTPUT / "run_summary.json", {"status": "integrated", "architecture": "MobileNetV3Ensemble",
            "validation": validation, "test": test, "temperature": temperature, "threshold": threshold,
            "parameters": ensemble.count_params(), "artifact_bytes": candidate.stat().st_size,
            "model_sha256": metadata["model_sha256"], "test_used_for_selection": False})
    print(json.dumps({"status": "integrated", "validation_top1": validation["top1_accuracy"],
                      "validation_macro_f1": validation["macro_f1"], "test_top1": test["top1_accuracy"],
                      "test_macro_f1": test["macro_f1"], "threshold": threshold, "temperature": temperature,
                      "desktop_median_ms": timing["desktop_median_ms"]}), flush=True)


def main():
    current = ROOT / "artifacts/model_metadata.json"
    if current.exists() and read_json(current).get("architecture") == "MobileNetV4SmallDistilled":
        raise ValueError("Receita histórica: reproduza o ensemble no checkout v1.1.2; modelo atual usa deploy_distilled.py.")

    parser = argparse.ArgumentParser(description=__doc__)
    modes = parser.add_mutually_exclusive_group()
    modes.add_argument("--train-only", action="store_true")
    modes.add_argument("--export-only", action="store_true")
    args = parser.parse_args()
    config = load_config(ROOT / "config.yaml")
    manifest = load_manifest(config, verify=True)
    classes = validate_manifest(manifest)
    if classes != read_json(ROOT / "artifacts/classes.json"):
        raise ValueError("Class order differs from the app")
    OUTPUT.mkdir(parents=True, exist_ok=True)
    CACHE.mkdir(parents=True, exist_ok=True)
    baseline_reference = OUTPUT / "baseline_reference.json"
    if not baseline_reference.exists():
        metadata = read_json(ROOT / "artifacts/model_metadata.json")
        if metadata["architecture"] != "MobileNetV3Small":
            raise ValueError("Missing original baseline reference")
        write_json(baseline_reference, {"source_commit": "7461f70de604cb4b4f4667065cd32d02141d3e17",
                   "metrics": read_json(ROOT / "artifacts/metrics.json"), "metadata": metadata,
                   "artifact_bytes": (ROOT / "artifacts/leafcare.tflite").stat().st_size})
    tf = initialize_tensorflow()
    if not args.export_only:
        write_json(OUTPUT / "protocol.json", {"members": MEMBERS, "fine_tuning_lr": 1e-5, "batch_size": config["batch_size"],
            "checkpoint_selection": "lowest validation loss, earlier/frozen on ties",
            "composition": "fixed historical validation-winning architectures/optimizers; all three retrained from ImageNet",
            "augmentation": "leafcare.training.make_dataset: flip, rotation .08, zoom .10, contrast .10; member seeds",
            "aggregation": "equal mean probabilities; temperature and threshold from validation only",
            "class_weights": "balanced training counts only", "dataset_split_unchanged": True,
            "test_used_for_selection": False})
        write_json(OUTPUT / "environment.json", {"python": platform.python_version(), "tensorflow": tf.__version__,
            "gpus": [tf.config.experimental.get_device_details(gpu).get("device_name") for gpu in tf.config.list_physical_devices("GPU")],
            "installed_packages": sorted(f"{p.metadata['Name']}=={p.version}" for p in distributions())})
        for spec in MEMBERS:
            train_member(spec, config, manifest)
    if not args.train_only:
        export(config, manifest)


if __name__ == "__main__":
    main()
