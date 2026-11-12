"""Replay a cycle's TFLite with the app's preprocessing and, with --install, put it in the app.

Runs in the export environment. Without --install nothing changes: the script
decodes every validation, TLA test and field test image the way the app does
(EXIF, sRGB, Pillow-equivalent bicubic resize to 256, center crop 224), runs the
TFLite and requires the same probabilities as training (max error 1e-4, same
Top-1) and the same metrics. With --install it replaces the model in
artifacts/ and in the Android assets, writes the metadata the app checks and the
reports in docs/model-reports/<name>/. Promotion changes the app's model: only
run --install when the team decided to ship this candidate.
"""
from __future__ import annotations

import argparse
import json
import shutil
from datetime import datetime, timezone
from pathlib import Path

import numpy as np

from experiments.dinov2.benchmark import classification_metrics, digest, encode_labels
from leafcare.common import classes_hash, read_json, write_json
from leafcare.preprocessing import DISTILLED_RESIZE, decode_rgb, preprocess, resize_distilled_rgb
from retraining.train import load_dataset

ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT.parent
ASSETS = REPO / "android-app/app/src/main/assets"
CHECKED = ("top1_accuracy", "macro_f1", "top3_accuracy", "coverage", "accepted_accuracy")


class Predictor:
    def __init__(self, path: Path):
        from ai_edge_litert.interpreter import Interpreter
        self.interpreter = Interpreter(model_path=str(path), num_threads=2)
        self.interpreter.allocate_tensors()
        self.inp = self.interpreter.get_input_details()[0]
        self.out = self.interpreter.get_output_details()[0]

    def scores(self, rgb: np.ndarray) -> np.ndarray:
        self.interpreter.set_tensor(self.inp["index"], rgb.astype(np.float32))
        self.interpreter.invoke()
        return self.interpreter.get_tensor(self.out["index"])[0]


def replay(run: Path) -> tuple[dict, dict, dict, dict]:
    config = read_json(run / "experiment_config.json")
    summary = read_json(run / "run_summary.json")
    export = read_json(run / "mobile_export.json")
    if summary.get("status") != "completed" or export.get("status") != "completed":
        raise ValueError("Treino ou exportação TFLite não concluídos.")
    model = run / export["path"]
    if digest(model) != export["sha256"]:
        raise ValueError("O TFLite mudou depois da exportação.")
    dataset, roots = load_dataset(run / "dataset.json", datetime.now(timezone.utc))
    classes = dataset["classes"]
    predictor = Predictor(model)
    results = {}
    with np.load(run / "logits.npz", allow_pickle=False) as recorded:
        for split in ("validation", "test", "field_test"):
            if split not in recorded.files:
                continue
            rows = [r for r in dataset["rows"] if r["split"] == split]
            probabilities = np.array([predictor.scores(resize_distilled_rgb(decode_rgb(roots[r["source"]] / r["path"]))[None, ...])
                                      for r in rows])
            logits = recorded[split] / config["temperature"]
            expected = np.exp(logits - logits.max(axis=1, keepdims=True))
            expected /= expected.sum(axis=1, keepdims=True)
            error = float(np.max(np.abs(probabilities - expected)))
            if error > 1e-4 or not np.array_equal(probabilities.argmax(1), expected.argmax(1)):
                raise ValueError(f"Replay com o pré-processamento do app divergiu em {split}: erro {error:.2e}.")
            truth = encode_labels(rows, classes)
            metrics = classification_metrics(truth, probabilities, classes)
            accepted = probabilities.max(axis=1) >= np.float32(config["threshold"])
            correct = probabilities.argmax(axis=1) == truth
            metrics.update(coverage=float(accepted.mean()), accepted=int(accepted.sum()),
                           accepted_correct=int(correct[accepted].sum()),
                           accepted_accuracy=float(correct[accepted].mean()) if accepted.any() else None)
            original = read_json(run / f"{split}_metrics.json")
            for key in CHECKED:
                if original[key] is not None and abs(metrics[key] - original[key]) > 1e-9:
                    raise ValueError(f"{split}: {key} do replay ({metrics[key]}) difere do treino ({original[key]}).")
            results[split] = {**metrics, "split": split, "model_sha256": export["sha256"], "threshold": config["threshold"],
                              "app_preprocessing_max_abs_error": error, "test_used_for_selection": False}
    return config, summary, export, results


def install(run: Path, name: str, config: dict, summary: dict, export: dict, results: dict, allow_unrecommended: bool):
    if summary.get("smoke_test"):
        raise ValueError("Execução de fumaça nunca é promovida.")
    if config["method"] != "distilled":
        raise ValueError("Só a receita definida (destilada) vai para o app; o treino supervisionado é diagnóstico.")
    if not summary.get("recommended_for_promotion") and not allow_unrecommended:
        raise ValueError("Candidato não recomendado: " + "; ".join(summary.get("reasons", [])) +
                         " Use --allow-unrecommended só com decisão registrada da equipe.")
    dataset = read_json(run / "dataset.json")
    classes = config["class_order"]
    report = REPO / "docs/model-reports" / name
    report.mkdir(parents=True, exist_ok=True)
    for split, metrics in results.items():
        write_json(report / f"{split}_metrics.json", metrics)
    for file in ("comparison.json", "mobile_export.json", "history.json", "probe_comparison.json"):
        shutil.copyfile(run / file, report / file)
    shutil.copyfile(run / "per_class_metrics.csv", report / "per_class_metrics.csv")
    write_json(report / "dataset_summary.json", {k: dataset[k] for k in (
        "created_at", "seed", "base_manifest_sha256", "field_export", "field_ratios", "field_groups", "counts",
        "split_sizes", "field_counts", "production_model_on_field_test", "rejected", "warnings")})
    sample = REPO / "samples/reference_frog_eye.jpg"
    predictor = Predictor(run / export["path"])
    write_json(report / "reference_prediction.json", {"image": sample.relative_to(REPO).as_posix(), "image_sha256": digest(sample),
               "model_sha256": export["sha256"], "probabilities": predictor.scores(preprocess(sample, resize=DISTILLED_RESIZE)).tolist()})
    metadata = {
        "schema_version": 2, "status": "trained", "architecture": "MobileNetV4SmallDistilled",
        "classes": classes, "classes_sha256": classes_hash(classes), "model_sha256": export["sha256"],
        "input_shape": [1, 224, 224, 3], "input_dtype": "float32", "output_shape": [1, len(classes)], "output_dtype": "float32",
        "resize": DISTILLED_RESIZE, "normalization": "embedded_imagenet_mean_std", "input_range": [0, 255], "max_image_pixels": 16000000,
        "preprocessing": {**config["preprocessing"], "short_side": 256, "center_crop": 224, "coefficient_precision_bits": 22},
        "temperature": config["temperature"], "confidence_threshold": config["threshold"], "threshold_calibrated": True,
        "probability_calibration": "embedded_temperature_scaling", "parameters": config["parameters"],
        "checkpoint_sha256": config["checkpoint_sha256"], "manifest_sha256": config["base_manifest_sha256"],
        "dataset_sha256": config["dataset_sha256"],
        "knowledge_distillation": {"teacher": "DINOv2-B partial finetune", "teacher_checkpoint_sha256": config["teacher"]["checkpoint_sha256"],
                                   "temperature": 4.0, "weight": 0.5},
        "source_experiment": f"runs/{run.name}", "report": f"docs/model-reports/{name}",
        "selected_epoch": config["selected_epoch"], "conversion_parity": export["parity"], "test_used_for_selection": False,
    }
    for folder in (ROOT / "artifacts", ASSETS):
        shutil.copyfile(run / export["path"], folder / "leafcare.tflite")
        write_json(folder / "model_metadata.json", metadata)
    write_json(ROOT / "artifacts/training_metadata.json", {**config, "architecture": "MobileNetV4SmallDistilled", "model_sha256": export["sha256"]})
    write_json(ROOT / "artifacts/metrics.json", {**results["test"], "status": "evaluated", "inference_backend": "TFLite float32",
               "classes": classes, "accuracy": results["test"]["top1_accuracy"], "threshold_calibrated": True})
    return metadata


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--run", required=True)
    parser.add_argument("--install", action="store_true", help="Substitui o modelo do app pelo candidato (decisão explícita).")
    parser.add_argument("--name", help="Pasta do relatório em docs/model-reports, por exemplo mobilenetv4-retreino-2026-12.")
    parser.add_argument("--allow-unrecommended", action="store_true")
    args = parser.parse_args()
    run = ROOT / "runs" / args.run
    config, summary, export, results = replay(run)
    output = {"replay": {split: {k: m[k] for k in CHECKED} for split, m in results.items()}, "installed": False}
    if args.install:
        if not args.name:
            raise SystemExit("Informe --name para a pasta do relatório.")
        install(run, args.name, config, summary, export, results, args.allow_unrecommended)
        output["installed"] = True
    print(json.dumps(output, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
