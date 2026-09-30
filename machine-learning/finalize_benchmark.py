"""Seleciona por validação, calibra, avalia teste e exporta TFLite."""
import argparse
import itertools
import json
import shutil
import tempfile
import time
from pathlib import Path

import numpy as np
from scipy.optimize import minimize_scalar
from sklearn.metrics import (
    accuracy_score,
    classification_report,
    confusion_matrix,
    f1_score,
    log_loss,
)

from leafcare.common import load_config, location, read_json, write_json, sha256, classes_hash
from leafcare.dataset import load_manifest
import tensorflow as tf
from leafcare.preprocessing import preprocess


def compute_metrics(y_true, y_pred):
    """Calcula métricas: accuracy, macro-F1, top-3, NLL."""
    y_pred_idx = y_pred.argmax(1)
    return {
        "accuracy": float(accuracy_score(y_true, y_pred_idx)),
        "macro_f1": float(f1_score(y_true, y_pred_idx, average="macro", zero_division=0)),
        "top3_accuracy": float(np.mean([v in np.argsort(-q)[:3] for v, q in zip(y_true, y_pred)])),
        "nll": float(log_loss(y_true, y_pred, labels=range(y_pred.shape[1]))),
    }


def predict_on_rows(model, config, rows):
    """Roda inferência Keras em lote nas linhas especificadas."""
    size = config["image_size"]

    def generator():
        for r in rows:
            yield preprocess(location(config, "dataset_dir") / r["path"], size)[0]

    ds = tf.data.Dataset.from_generator(
        generator,
        output_signature=tf.TensorSpec((size, size, 3), tf.float32),
    ).batch(config["batch_size"]).prefetch(1)

    return model.predict(ds, verbose=0)


def temperature_scale(probs, temperature):
    """Aplica temperature scaling às probabilidades."""
    z = np.log(np.clip(probs, 1e-7, 1)) / temperature
    z -= z.max(1, keepdims=True)
    z = np.exp(z)
    return z / z.sum(1, keepdims=True)


def select_threshold(probs, y_true):
    """Seleciona threshold que maximiza cobertura mantendo accepted_accuracy >= 90%."""
    y_pred = probs.argmax(1)
    confidence = probs.max(1)
    curve = []

    for t in np.arange(0.45, 0.951, 0.01):
        accepted = confidence >= t
        if accepted.sum() >= 10:
            curve.append({
                "threshold": round(float(t), 2),
                "coverage": float(accepted.mean()),
                "accepted_accuracy": float((y_pred[accepted] == y_true[accepted]).mean()),
                "accepted": int(accepted.sum()),
            })

    eligible = [r for r in curve if r["accepted_accuracy"] >= 0.90]
    if eligible:
        return max(eligible, key=lambda r: r["coverage"]), curve
    # Fallback: melhor accuracy entre os com coverage >= 50%
    fallback = [r for r in curve if r["coverage"] >= 0.5]
    return max(fallback, key=lambda r: r["accepted_accuracy"]), curve


def convert_to_tflite(model, output_path, quantized=False):
    """Converte modelo Keras para TFLite (float32 ou dynamic quant)."""
    with tempfile.TemporaryDirectory() as tmpdir:
        model.export(tmpdir, format="tf_saved_model")
        converter = tf.lite.TFLiteConverter.from_saved_model(tmpdir)
        converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS]
        if quantized:
            converter.optimizations = [tf.lite.Optimize.DEFAULT]
        output_path.write_bytes(converter.convert())


def evaluate_tflite(model_path, rows, config, num_classes):
    """Avalia modelo TFLite nas linhas de teste, retorna probabilidades e tempos."""
    interpreter = tf.lite.Interpreter(model_path=str(model_path), num_threads=4)
    interpreter.allocate_tensors()
    input_idx = interpreter.get_input_details()[0]["index"]
    output_idx = interpreter.get_output_details()[0]["index"]

    scores = []
    times = []
    for r in rows:
        x = preprocess(location(config, "dataset_dir") / r["path"], config["image_size"])
        start = time.perf_counter()
        interpreter.set_tensor(input_idx, x)
        interpreter.invoke()
        times.append((time.perf_counter() - start) * 1000)
        scores.append(interpreter.get_tensor(output_idx)[0])

    probs = np.asarray(scores)
    assert probs.shape == (len(rows), num_classes)
    return probs, times


def main(config):
    manifest = load_manifest(config)
    root = location(config, "benchmark_dir")
    ranking = read_json(root / "ranking_validation.json")
    classes = manifest["classes"]

    if len(ranking) < 14:
        raise ValueError(f"Benchmark incompleto: {len(ranking)}/14")

    # Splits de validação e teste
    val_rows = [r for r in manifest["rows"] if r["split"] == "validation"]
    test_rows = [r for r in manifest["rows"] if r["split"] == "test"]
    y_val = np.array([classes.index(r["class_id"]) for r in val_rows])
    y_test = np.array([classes.index(r["class_id"]) for r in test_rows])

    # Pool de candidatos com probabilidades de validação
    pool = [
        {
            "id": r["id"],
            "path": root / r["id"] / "model.keras",
            "p": np.load(root / r["id"] / "validation_probabilities.npy"),
            "parameters": r["parameters"],
        }
        for r in ranking
    ]

    # Adiciona baseline anterior se existir
    baseline_path = location(config, "output_dir") / "model.keras"
    if baseline_path.exists():
        model = tf.keras.models.load_model(baseline_path, compile=False)
        pool.append({
            "id": "baseline_anterior",
            "path": baseline_path,
            "p": predict_on_rows(model, config, val_rows),
            "parameters": model.count_params(),
        })
        del model

    # Modelos individuais
    singles = [
        {"members": [x["id"]], "parameters": x["parameters"], **compute_metrics(y_val, x["p"])}
        for x in pool
    ]

    # Ensembles dos 6 melhores por macro-F1
    top6 = sorted(pool, key=lambda x: compute_metrics(y_val, x["p"])["macro_f1"], reverse=True)[:6]
    ensembles = []
    for n in (2, 3):
        for combo in itertools.combinations(top6, n):
            avg_probs = np.mean([x["p"] for x in combo], axis=0)
            ensembles.append({
                "members": [x["id"] for x in combo],
                "parameters": sum(x["parameters"] for x in combo),
                **compute_metrics(y_val, avg_probs),
            })

    # Seleção: melhor macro-F1, com ganho mínimo de 0.01 sobre melhor single
    candidates = sorted(
        singles + ensembles,
        key=lambda x: (x["macro_f1"], x["accuracy"], x["top3_accuracy"], -x["parameters"]),
        reverse=True,
    )
    best_single = max(singles, key=lambda x: (x["macro_f1"], x["accuracy"]))
    best = candidates[0]
    if len(best["members"]) > 1 and best["macro_f1"] - best_single["macro_f1"] < 0.01:
        best = best_single

    # Calibração: temperature scaling na validação
    p_val = np.mean([next(x["p"] for x in pool if x["id"] == name) for name in best["members"]], axis=0)
    opt = minimize_scalar(
        lambda t: log_loss(y_val, temperature_scale(p_val, t), labels=range(len(classes))),
        bounds=(0.35, 3),
        method="bounded",
    )
    temperature = float(opt.x)
    threshold_info, threshold_curve = select_threshold(temperature_scale(p_val, temperature), y_val)

    # Exporta modelos do ensemble para deployment
    deploy_dir = root / "deployment"
    deploy_dir.mkdir(exist_ok=True)
    model_info = []
    test_parts = []

    for i, name in enumerate(best["members"], 1):
        source = next(x["path"] for x in pool if x["id"] == name)
        model = tf.keras.models.load_model(source, compile=False)

        # Probabilidades de teste (Keras)
        keras_test_probs = predict_on_rows(model, config, test_rows)
        test_parts.append(keras_test_probs)

        # Copia .keras
        keras_dest = deploy_dir / f"model_{i}_{name}.keras"
        shutil.copy2(source, keras_dest)

        # Converte TFLite float32 e dynamic
        float32_path = deploy_dir / f"model_{i}_{name}_float32.tflite"
        dynamic_path = deploy_dir / f"model_{i}_{name}_dynamic.tflite"
        convert_to_tflite(model, float32_path, quantized=False)
        convert_to_tflite(model, dynamic_path, quantized=True)

        # Avalia TFLite no teste
        float32_probs, times = evaluate_tflite(float32_path, test_rows, config, len(classes))
        dynamic_probs, _ = evaluate_tflite(dynamic_path, test_rows, config, len(classes))

        model_info.append({
            "id": name,
            "keras": keras_dest.name,
            "float32": float32_path.name,
            "dynamic": dynamic_path.name,
            "float32_bytes": float32_path.stat().st_size,
            "dynamic_bytes": dynamic_path.stat().st_size,
            "float32_sha256": sha256(float32_path),
            "dynamic_sha256": sha256(dynamic_path),
            "float32_max_abs_error": float(np.max(np.abs(keras_test_probs - float32_probs))),
            "dynamic_max_abs_error": float(np.max(np.abs(keras_test_probs - dynamic_probs))),
            "float32_top1_agreement": float(np.mean(keras_test_probs.argmax(1) == float32_probs.argmax(1))),
            "dynamic_top1_agreement": float(np.mean(keras_test_probs.argmax(1) == dynamic_probs.argmax(1))),
            "desktop_median_ms": float(np.median(times)),
        })

    # Métricas finais no teste (ensemble calibrado)
    p_test = temperature_scale(np.mean(test_parts, axis=0), temperature)
    test_result = compute_metrics(y_test, p_test)
    y_pred_test = p_test.argmax(1)
    accepted = p_test.max(1) >= threshold_info["threshold"]

    test_result.update({
        "n_images": len(y_test),
        "coverage": float(accepted.mean()),
        "accepted_accuracy": float((y_pred_test[accepted] == y_test[accepted]).mean()) if accepted.any() else None,
        "classification_report": classification_report(
            y_test, y_pred_test, labels=range(len(classes)), target_names=classes, output_dict=True, zero_division=0
        ),
        "confusion_matrix": confusion_matrix(y_test, y_pred_test, labels=range(len(classes))).tolist(),
    })

    # Manifesto de deployment
    manifest = {
        "schema_version": 2,
        "input_shape": [1, config["image_size"], config["image_size"], 3],
        "input_dtype": "float32",
        "pixel_range": [0, 255],
        "classes": classes,
        "classes_sha256": classes_hash(classes),
        "ensemble": "mean_probabilities",
        "members": model_info,
        "temperature": temperature,
        "confidence_threshold": threshold_info["threshold"],
        "selection_validation": best,
        "test_used_for_selection": False,
        "test_metrics": test_result,
    }

    write_json(deploy_dir / "ranking_validation.json", candidates)
    write_json(deploy_dir / "threshold_validation.json", {"temperature": temperature, "selected": threshold_info, "curve": threshold_curve})
    write_json(deploy_dir / "deployment_manifest.json", manifest)
    write_json(deploy_dir / "test_predictions.json", [
        {"path": r["path"], "true_class": r["class_id"], "probabilities": p.tolist()}
        for r, p in zip(test_rows, p_test)
    ])

    # Output resumido para console
    print(json.dumps({
        "selected": best,
        "temperature": temperature,
        "threshold": threshold_info,
        "test": {k: v for k, v in test_result.items() if k not in {"classification_report", "confusion_matrix"}},
        "models": model_info,
    }, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--config", default="config.benchmark.yaml")
    args = parser.parse_args()
    main(load_config(args.config))