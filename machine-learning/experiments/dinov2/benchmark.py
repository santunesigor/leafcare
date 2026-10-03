"""Frozen-feature DINOv2 ViT-S/14 benchmark on LeafCare's existing split."""
import argparse
import csv
import hashlib
import json
import platform
import random
import statistics
import time
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path

import numpy as np
from scipy.optimize import minimize_scalar
from scipy.special import logsumexp, softmax
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import accuracy_score, classification_report, confusion_matrix, f1_score

from leafcare.common import load_config, write_json
from leafcare.dataset import load_manifest
from leafcare.preprocessing import decode_rgb

ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / "benchmark_artifacts" / "dinov2"
CACHE = ROOT / "experiments" / "dinov2" / ".cache"
REPO_REVISION = "7764ea0f912e53c92e82eb78a2a1631e92725fc8"
EXPECTED_SPLITS = {"train": 489, "validation": 104, "test": 103}
TARGET_ACCEPTED_ACCURACY = 0.90
IMAGE_SIZE = 224
RESIZE_SIZE = 256
BATCH_SIZE = 8
SEED = 42
CACHE_VERSION = "v1_eval256_bicubic_cls_mean"


def digest(path):
    result = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            result.update(block)
    return result.hexdigest()


def manifest_digest(path):
    # Git may check the tracked JSON out with CRLF on Windows; hash its canonical LF bytes.
    return hashlib.sha256(Path(path).read_bytes().replace(b"\r\n", b"\n")).hexdigest()


def validate_manifest(manifest, expected_splits=EXPECTED_SPLITS):
    classes = manifest.get("classes", [])
    rows = manifest.get("rows", [])
    if len(classes) != 16 or len(set(classes)) != 16:
        raise ValueError("O manifesto precisa preservar as 16 classes ordenadas.")
    if len({row["path"] for row in rows}) != len(rows):
        raise ValueError("O manifesto contém caminhos duplicados.")
    counts = Counter(row["split"] for row in rows)
    if dict(counts) != expected_splits:
        raise ValueError(f"Split inesperado: {dict(counts)}.")
    if any(row["class_id"] not in classes for row in rows):
        raise ValueError("Classe ausente do mapeamento do manifesto.")
    for split in expected_splits:
        if {row["class_id"] for row in rows if row["split"] == split} != set(classes):
            raise ValueError(f"O split {split} não contém todas as classes.")
    for field in ("group_id", "pixel_sha256"):
        seen = {}
        for row in rows:
            value = row[field]
            previous = seen.setdefault(value, row["split"])
            if previous != row["split"]:
                raise ValueError(f"Vazamento de {field} entre splits.")
    return classes


def split_rows(manifest, split):
    if split not in EXPECTED_SPLITS:
        raise ValueError(f"Split desconhecido: {split}.")
    return [row for row in manifest["rows"] if row["split"] == split]


def encode_labels(rows, classes):
    indices = {name: i for i, name in enumerate(classes)}
    try:
        return np.asarray([indices[row["class_id"]] for row in rows], dtype=np.int64)
    except KeyError as error:
        raise ValueError(f"Classe não mapeada: {error.args[0]}.") from error


def classification_metrics(truth, logits, classes):
    scores = np.asarray(logits, dtype=np.float64)
    predicted = scores.argmax(axis=1)
    order = np.argsort(-scores, axis=1, kind="stable")[:, :3]
    top3_correct = int(np.any(order == np.asarray(truth)[:, None], axis=1).sum())
    return {
        "n_images": int(len(truth)),
        "top1_correct": int((predicted == truth).sum()),
        "top1_accuracy": float(accuracy_score(truth, predicted)),
        "macro_f1": float(f1_score(truth, predicted, labels=list(range(len(classes))),
                                    average="macro", zero_division=0)),
        "top3_correct": top3_correct,
        "top3_accuracy": float(top3_correct / len(truth)),
        "classification_report": classification_report(
            truth, predicted, labels=list(range(len(classes))),
            target_names=classes, output_dict=True, zero_division=0,
        ),
        "confusion_matrix": confusion_matrix(truth, predicted, labels=list(range(len(classes)))).tolist(),
    }


def expected_calibration_error(truth, probabilities, bins=15):
    confidence = probabilities.max(axis=1)
    correct = probabilities.argmax(axis=1) == truth
    edges = np.linspace(0.0, 1.0, bins + 1)
    total = len(truth)
    return float(sum(
        (selected.sum() / total) * abs(float(correct[selected].mean()) - float(confidence[selected].mean()))
        for index in range(bins)
        if (selected := ((confidence > edges[index]) & (confidence <= edges[index + 1]))).any()
    ))


def calibration_metrics(truth, logits, temperature):
    probabilities = softmax(logits / temperature, axis=1)
    nll = float(-np.mean(logits[np.arange(len(truth)), truth] / temperature
                         - logsumexp(logits / temperature, axis=1)))
    return {
        "temperature": float(temperature),
        "negative_log_likelihood": nll,
        "expected_calibration_error_15_bins": expected_calibration_error(truth, probabilities),
        "probabilities": probabilities,
    }


def fit_temperature(truth, logits):
    before = calibration_metrics(truth, logits, 1.0)

    def objective(log_temperature):
        return calibration_metrics(truth, logits, float(np.exp(log_temperature)))[
            "negative_log_likelihood"]

    result = minimize_scalar(objective, bounds=(-3.0, 3.0), method="bounded")
    candidate = float(np.exp(result.x))
    after = calibration_metrics(truth, logits, candidate)
    selected = candidate if after["negative_log_likelihood"] < before["negative_log_likelihood"] else 1.0
    return selected, {
        "method": "temperature scaling minimizing validation NLL",
        "before": {k: v for k, v in before.items() if k != "probabilities"},
        "optimized": {k: v for k, v in after.items() if k != "probabilities"},
        "applied": selected != 1.0,
        "selected_temperature": selected,
    }


def select_threshold(truth, probabilities, target=TARGET_ACCEPTED_ACCURACY):
    confidence = probabilities.max(axis=1)
    correct = probabilities.argmax(axis=1) == truth
    candidates = sorted(set(float(value) for value in confidence))
    options = []
    for threshold in candidates:
        accepted = confidence >= threshold
        count = int(accepted.sum())
        accuracy = float(correct[accepted].mean()) if count else 0.0
        options.append((threshold, count, accuracy))
    eligible = [item for item in options if item[2] >= target]
    if eligible:
        threshold, count, accuracy = max(eligible, key=lambda item: (item[1], -item[0]))
        fallback = False
    else:
        threshold, count, accuracy = max(options, key=lambda item: (item[2], item[1], -item[0]))
        fallback = True
    return threshold, {
        "target_accepted_accuracy": target,
        "selected_validation_accepted_accuracy": accuracy,
        "selected_validation_accepted": count,
        "selected_validation_coverage": count / len(truth),
        "target_reached": not fallback,
        "fallback": "maximize accuracy, then coverage" if fallback else None,
    }


def save_confusion(path, matrix, classes):
    with Path(path).open("w", newline="", encoding="utf-8") as stream:
        writer = csv.writer(stream)
        writer.writerow(["true_class\\predicted_class", *classes])
        for label, values in zip(classes, matrix):
            writer.writerow([label, *values])


def save_class_metrics(path, results, classes):
    fields = ["split", "class", "precision", "recall", "f1", "support", "statistical_note"]
    with Path(path).open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=fields)
        writer.writeheader()
        for split, metrics in results.items():
            report = metrics["classification_report"]
            for name in classes:
                values = report[name]
                support = int(values["support"])
                writer.writerow({
                    "split": split, "class": name,
                    "precision": values["precision"], "recall": values["recall"],
                    "f1": values["f1-score"], "support": support,
                    "statistical_note": "instável: suporte 1 ou 2" if support <= 2 else "",
                })


def save_predictions(path, rows, truth, logits, probabilities, threshold, classes):
    predicted = logits.argmax(axis=1)
    top3 = np.argsort(-logits, axis=1, kind="stable")[:, :3]
    with Path(path).open("w", newline="", encoding="utf-8") as stream:
        fields = ["path", "true_class", "predicted_class", "confidence", "accepted", "top3_classes"]
        writer = csv.DictWriter(stream, fieldnames=fields)
        writer.writeheader()
        for index, row in enumerate(rows):
            writer.writerow({
                "path": row["path"], "true_class": classes[truth[index]],
                "predicted_class": classes[predicted[index]],
                "confidence": float(probabilities[index].max()),
                "accepted": bool(probabilities[index].max() >= threshold),
                "top3_classes": "|".join(classes[item] for item in top3[index]),
            })


def run():
    import torch
    from PIL import Image
    from torch.utils.data import DataLoader, Dataset
    from torchvision import transforms
    from torchvision.transforms import InterpolationMode

    config = load_config(ROOT / "config.yaml")
    manifest_path = ROOT / config["prepared_dir"] / "manifest.json"
    manifest = load_manifest(config, verify=True)
    classes = validate_manifest(manifest)
    app_classes = json.loads((ROOT / "artifacts" / "classes.json").read_text(encoding="utf-8"))
    if classes != app_classes:
        raise ValueError("A ordem de classes do manifesto diverge do bundle integrado.")
    manifest_sha256 = manifest_digest(manifest_path)
    if manifest.get("seed") != SEED:
        raise ValueError(f"Seed do manifesto mudou: {manifest.get('seed')}.")
    OUTPUT.mkdir(parents=True, exist_ok=True)
    CACHE.mkdir(parents=True, exist_ok=True)

    random.seed(SEED)
    np.random.seed(SEED)
    torch.manual_seed(SEED)
    if torch.cuda.is_available():
        torch.cuda.manual_seed_all(SEED)
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")

    transform = transforms.Compose([
        transforms.Resize(RESIZE_SIZE, interpolation=InterpolationMode.BICUBIC, antialias=True),
        transforms.CenterCrop(IMAGE_SIZE),
        transforms.ToTensor(),
        transforms.Normalize((0.485, 0.456, 0.406), (0.229, 0.224, 0.225)),
    ])

    class ManifestImages(Dataset):
        def __init__(self, rows):
            self.rows = rows

        def __len__(self):
            return len(self.rows)

        def __getitem__(self, index):
            rgb = decode_rgb(ROOT / config["dataset_dir"] / self.rows[index]["path"])
            return transform(Image.fromarray(rgb)), index

    model = torch.hub.load(
        f"facebookresearch/dinov2:{REPO_REVISION}", "dinov2_vits14",
        trust_repo=True, skip_validation=True,
    )
    model.to(device).eval()
    for parameter in model.parameters():
        parameter.requires_grad_(False)
    parameter_count = sum(parameter.numel() for parameter in model.parameters())

    def extract(split):
        rows = split_rows(manifest, split)
        labels = encode_labels(rows, classes)
        cache_file = CACHE / f"{CACHE_VERSION}_{REPO_REVISION[:10]}_{manifest_sha256[:12]}_{split}.npz"
        if cache_file.exists():
            cached = np.load(cache_file, allow_pickle=False)
            if cached["paths"].tolist() == [row["path"] for row in rows]:
                return cached["features"], labels
        loader = DataLoader(ManifestImages(rows), batch_size=BATCH_SIZE, shuffle=False,
                            num_workers=0, pin_memory=device.type == "cuda")
        batches = []
        with torch.inference_mode():
            for images, _ in loader:
                output = model.forward_features(images.to(device, non_blocking=True))
                feature = torch.cat(
                    (output["x_norm_clstoken"], output["x_norm_patchtokens"].mean(dim=1)), dim=1)
                batches.append(feature.float().cpu().numpy())
        features = np.concatenate(batches)
        np.savez_compressed(cache_file, features=features, paths=np.asarray([r["path"] for r in rows]))
        return features, labels

    extraction_start = time.perf_counter()
    train_x, train_y = extract("train")
    val_x, val_y = extract("validation")
    feature_seconds = time.perf_counter() - extraction_start
    val_rows = split_rows(manifest, "validation")
    val_logits = []
    candidates = []
    classifiers = {}
    train_start = time.perf_counter()
    for name, class_weight in (("unweighted", None), ("balanced", "balanced")):
        classifier = LogisticRegression(
            C=1.0, class_weight=class_weight, max_iter=1000,
            solver="lbfgs", random_state=SEED, tol=1e-4,
        )
        start = time.perf_counter()
        classifier.fit(train_x, train_y)
        elapsed = time.perf_counter() - start
        logits = classifier.decision_function(val_x)
        metrics = classification_metrics(val_y, logits, classes)
        classifiers[name] = classifier
        candidates.append({
            "name": name, "class_weight": class_weight, "C": 1.0,
            "optimizer": "scikit-learn LogisticRegression (lbfgs)",
            "max_iter": 1000, "iterations": classifier.n_iter_.tolist(),
            "training_seconds": elapsed, "validation": {
                key: metrics[key] for key in ("n_images", "top1_correct", "top1_accuracy",
                                              "macro_f1", "top3_correct", "top3_accuracy")
            },
        })
        val_logits.append(logits)
    probe_seconds = time.perf_counter() - train_start
    # Macro-F1 is the predeclared primary validation metric; ties use Top-1, then Top-3.
    best_index = max(range(len(candidates)), key=lambda index: (
        candidates[index]["validation"]["macro_f1"],
        candidates[index]["validation"]["top1_accuracy"],
        candidates[index]["validation"]["top3_accuracy"],
        candidates[index]["name"] == "unweighted",
    ))
    selected_name = candidates[best_index]["name"]
    selected_classifier = classifiers[selected_name]
    selected_val_logits = val_logits[best_index]
    temperature, calibration = fit_temperature(val_y, selected_val_logits)
    calibrated = calibration_metrics(val_y, selected_val_logits, temperature)
    threshold, threshold_info = select_threshold(val_y, calibrated["probabilities"])
    val_metrics = classification_metrics(val_y, selected_val_logits, classes)
    val_metrics.update({
        "temperature_applied": calibration["applied"],
        "calibration": calibration,
        "threshold": threshold,
        "threshold_selection": threshold_info,
        "coverage": threshold_info["selected_validation_coverage"],
        "accepted_accuracy": threshold_info["selected_validation_accepted_accuracy"],
    })

    # Single-image PC timing on validation data; never use the held-out test for profiling.
    sample = val_rows[0]
    durations = []
    for iteration in range(25):
        image = transform(Image.fromarray(decode_rgb(ROOT / config["dataset_dir"] / sample["path"])))
        tensor = image.unsqueeze(0).to(device)
        if device.type == "cuda":
            torch.cuda.synchronize()
        start = time.perf_counter()
        with torch.inference_mode():
            model.forward_features(tensor)
        if device.type == "cuda":
            torch.cuda.synchronize()
        if iteration >= 5:
            durations.append((time.perf_counter() - start) * 1000)

    write_json(OUTPUT / "probe_comparison.json", candidates)
    write_json(OUTPUT / "validation_metrics.json", val_metrics)
    np.savez_compressed(OUTPUT / "classifier.npz", classes=np.asarray(classes),
                        coef=selected_classifier.coef_, intercept=selected_classifier.intercept_)
    write_json(OUTPUT / "experiment_config.json", {
        "model": "DINOv2 ViT-S/14", "checkpoint": "dinov2_vits14_pretrain.pth",
        "checkpoint_url": "https://dl.fbaipublicfiles.com/dinov2/dinov2_vits14/dinov2_vits14_pretrain.pth",
        "license": "Apache-2.0 (official DINOv2 model card)",
        "official_repository": "https://github.com/facebookresearch/dinov2",
        "repository_revision": REPO_REVISION,
        "checkpoint_sha256": digest(Path(torch.hub.get_dir()) / "checkpoints" / "dinov2_vits14_pretrain.pth"),
        "checkpoint_bytes": (Path(torch.hub.get_dir()) / "checkpoints" / "dinov2_vits14_pretrain.pth").stat().st_size,
        "seed": SEED, "manifest_sha256": manifest_sha256,
        "manifest_classes_sha256": manifest["classes_sha256"],
        "split_counts": EXPECTED_SPLITS, "class_order": classes,
        "preprocessing": {
            "resize": "shortest edge 256; preserve aspect ratio",
            "crop": "center crop 224x224",
            "interpolation": "bicubic with antialiasing",
            "tensor": "RGB float32 scaled from [0,255] to [0,1]",
            "normalization_mean": [0.485, 0.456, 0.406],
            "normalization_std": [0.229, 0.224, 0.225],
            "training_augmentation": "none; deterministic evaluation transform on train/validation",
        },
        "feature": "concatenate normalized CLS token and mean normalized patch tokens",
        "feature_dimension": int(train_x.shape[1]),
        "classifier_selected_on_validation": selected_name,
        "selection_metric": "validation Macro-F1; ties by Top-1, then Top-3",
        "classifier_C": 1.0, "optimizer": "lbfgs", "max_iter": 1000,
        "learning_rate": "not applicable to LBFGS",
        "epochs": "not applicable; convergence capped at 1000 iterations",
        "early_stopping": "not used; LBFGS convergence and validation probe selection",
        "class_weight": "balanced" if selected_name == "balanced" else None,
        "batch_size": BATCH_SIZE, "temperature": temperature, "threshold": threshold,
        "test_used_for_selection": False,
        "test_was_not_loaded_until_model_calibration_and_threshold_were_frozen": True,
        "total_parameters": int(parameter_count + selected_classifier.coef_.size + selected_classifier.intercept_.size),
        "backbone_parameters": int(parameter_count),
        "trainable_parameters": int(selected_classifier.coef_.size + selected_classifier.intercept_.size),
        "feature_extraction_seconds_train_validation": feature_seconds,
        "classifier_training_seconds": probe_seconds,
    })

    # Configuration, calibration, and abstention threshold are now frozen using train/validation only.
    test_rows = split_rows(manifest, "test")
    test_x, test_y = extract("test")
    test_logits = selected_classifier.decision_function(test_x)
    test_probabilities = softmax(test_logits / temperature, axis=1)
    test_metrics = classification_metrics(test_y, test_logits, classes)
    accepted = test_probabilities.max(axis=1) >= threshold
    test_metrics.update({
        "split": "test", "temperature": temperature, "threshold": threshold,
        "coverage": float(accepted.mean()),
        "accepted": int(accepted.sum()),
        "accepted_correct": int(((test_probabilities.argmax(axis=1) == test_y) & accepted).sum()),
        "accepted_accuracy": float((test_probabilities.argmax(axis=1)[accepted] == test_y[accepted]).mean())
        if accepted.any() else None,
        "test_used_for_selection": False,
    })
    write_json(OUTPUT / "test_metrics.json", test_metrics)
    save_confusion(OUTPUT / "confusion_matrix.csv", test_metrics["confusion_matrix"], classes)
    save_confusion(OUTPUT / "confusion_matrix_validation.csv", val_metrics["confusion_matrix"], classes)
    save_class_metrics(OUTPUT / "per_class_metrics.csv", {"validation": val_metrics, "test": test_metrics}, classes)
    save_predictions(OUTPUT / "predictions_test.csv", test_rows, test_y, test_logits, test_probabilities,
                     threshold, classes)

    checkpoint = Path(torch.hub.get_dir()) / "checkpoints" / "dinov2_vits14_pretrain.pth"
    environment = {
        "python": platform.python_version(), "platform": platform.platform(),
        "torch": torch.__version__, "torchvision": __import__("torchvision").__version__,
        "scikit_learn": __import__("sklearn").__version__, "numpy": np.__version__,
        "pillow": __import__("PIL").__version__, "scipy": __import__("scipy").__version__,
        "cuda_runtime": torch.version.cuda, "cuda_available": torch.cuda.is_available(),
        "device": str(device),
        "gpu": torch.cuda.get_device_name(0) if torch.cuda.is_available() else None,
        "gpu_memory_bytes": torch.cuda.get_device_properties(0).total_memory if torch.cuda.is_available() else None,
        "checkpoint_sha256": digest(checkpoint), "checkpoint_bytes": checkpoint.stat().st_size,
        "dinov2_repository_revision": REPO_REVISION,
    }
    write_json(OUTPUT / "environment.json", environment)
    write_json(OUTPUT / "run_summary.json", {
        "status": "completed", "model": "DINOv2 ViT-S/14 frozen linear probe",
        "selected_probe": selected_name, "validation_macro_f1": val_metrics["macro_f1"],
        "test_top1_accuracy": test_metrics["top1_accuracy"],
        "test_top1_correct": test_metrics["top1_correct"],
        "test_macro_f1": test_metrics["macro_f1"],
        "test_top3_accuracy": test_metrics["top3_accuracy"],
        "test_top3_correct": test_metrics["top3_correct"],
        "temperature": temperature, "threshold": threshold,
        "coverage": test_metrics["coverage"], "accepted_accuracy": test_metrics["accepted_accuracy"],
        "desktop_single_image_ms_median": statistics.median(durations),
        "desktop_single_image_ms_p95": float(np.percentile(durations, 95)),
        "desktop_timing_note": "Batch 1 backbone forward; excludes decode/preprocessing/host-to-device copy. Este tempo não representa latência em aparelho Android.",
        "android_inference": "não medido", "mobile_export": "não tentado",
        "test_opened_after_freezing_model_temperature_and_threshold": True,
        "test_used_for_selection": False,
    })
    print(json.dumps({
        "selected_probe": selected_name,
        "validation": {k: val_metrics[k] for k in ("top1_accuracy", "macro_f1", "top3_accuracy")},
        "test": {k: test_metrics[k] for k in ("top1_accuracy", "top1_correct", "macro_f1",
                                                "top3_accuracy", "top3_correct", "coverage",
                                                "accepted_accuracy")},
        "temperature": temperature, "threshold": threshold,
        "artifacts": str(OUTPUT),
    }, indent=2))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--validate-only", action="store_true",
                        help="valida o manifesto e as partições sem carregar DINOv2.")
    args = parser.parse_args()
    if args.validate_only:
        config = load_config(ROOT / "config.yaml")
        manifest = load_manifest(config, verify=True)
        validate_manifest(manifest)
        print("Manifesto íntegro: 696 imagens, split fixo 489/104/103, sem grupos sobrepostos.")
        return
    run()


if __name__ == "__main__":
    main()
