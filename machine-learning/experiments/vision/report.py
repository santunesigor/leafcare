"""Generate the comparison CSV from recorded artifacts, without running inference."""
import csv
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / "benchmark_artifacts/vision"
HISTORICAL_NAMES = {
    "m3s_low_dropout": "MobileNetV3Small, dropout 0,15",
    "m3s_rmsprop": "MobileNetV3Small, RMSprop",
    "m3s_no_weights": "MobileNetV3Small, sem pesos de classe",
    "m3s_adam": "MobileNetV3Small, Adam",
    "m3l_base": "MobileNetV3Large, base",
    "m3s_base": "MobileNetV3Small, base",
    "m2_base": "MobileNetV2, base",
    "effb0_strong": "EfficientNetB0, augmentation forte",
    "m3s_strong": "MobileNetV3Small, augmentation forte",
    "m3l_strong": "MobileNetV3Large, augmentation forte",
    "effb0_base": "EfficientNetB0, base",
    "effv2b0_base": "EfficientNetV2B0, base",
    "nasnet_base": "NASNetMobile, base",
    "effv2b0_strong": "EfficientNetV2B0, augmentation forte",
}
FIELDS = [
    "id", "model", "status", "method", "input_height", "input_width", "parameters",
    "validation_top1", "validation_macro_f1", "validation_top3",
    "test_top1", "test_macro_f1", "test_top3", "temperature", "threshold",
    "test_coverage", "test_accepted_accuracy", "artifact_format", "artifact_bytes",
    "desktop_median_ms", "desktop_p95_ms", "timing_device", "peak_cuda_batch1_bytes",
    "android_latency_ms", "source",
]


def read(path):
    return json.loads(Path(path).read_text(encoding="utf-8"))


def metrics_fields(validation=None, test=None):
    result = {}
    for prefix, metrics in (("validation", validation), ("test", test)):
        if metrics:
            result.update({
                prefix + "_top1": metrics.get("top1_accuracy", metrics.get("accuracy")),
                prefix + "_macro_f1": metrics["macro_f1"],
                prefix + "_top3": metrics.get("top3_accuracy"),
            })
    return result


def build_rows():
    historical = read(OUTPUT / "historical_summary.json")
    members = {m["id"]: m for m in historical["ensemble"]["members"]}
    rows = []
    for candidate in historical["candidates"]:
        model_id = candidate["id"]
        member = members.get(model_id)
        rows.append({
            "id": model_id, "model": HISTORICAL_NAMES[model_id], "status": "historical_candidate",
            "method": "transfer learning + fine-tuning", "input_height": 224, "input_width": 224,
            "parameters": candidate["parameters"], **metrics_fields(candidate),
            "artifact_format": "TFLite float32" if member else "Keras archive (includes optimizer state)",
            "artifact_bytes": member["float32_bytes"] if member else candidate["keras_bytes"],
            "desktop_median_ms": member["desktop_median_ms"] if member else None,
            "timing_device": "historical desktop CPU (TFLite); hardware not recorded" if member else None,
            "source": historical["source_commit"],
        })
    integrated = read(ROOT / "artifacts/metrics.json")
    rows.append({
        "id": "baseline_anterior", "model": "MobileNetV3Small integrado", "status": "integrated",
        "method": "transfer learning + fine-tuning", "input_height": 224, "input_width": 224,
        "parameters": historical["baseline"]["parameters"],
        **metrics_fields(historical["baseline"], integrated), "temperature": 1.0,
        "threshold": integrated["threshold"], "test_coverage": integrated["coverage"],
        "test_accepted_accuracy": integrated["accepted_accuracy"], "artifact_format": "TFLite float32",
        "artifact_bytes": (ROOT / "artifacts/leafcare.tflite").stat().st_size,
        "source": "artifacts/metrics.json; historical baseline parameter count",
    })
    ensemble = historical["ensemble"]
    rows.append({
        "id": "ensemble", "model": "Ensemble: 2 MobileNetV3Small + MobileNetV3Large", "status": "historical_experimental",
        "method": "mean probabilities of 3 fine-tuned models", "input_height": 224, "input_width": 224,
        "parameters": ensemble["selection_validation"]["parameters"],
        **metrics_fields(ensemble["selection_validation"], ensemble["test_metrics"]),
        "temperature": ensemble["temperature"], "threshold": ensemble["confidence_threshold"],
        "test_coverage": ensemble["test_metrics"]["coverage"],
        "test_accepted_accuracy": ensemble["test_metrics"]["accepted_accuracy"],
        "artifact_format": "3 TFLite float32", "artifact_bytes": sum(m["float32_bytes"] for m in members.values()),
        "source": historical["source_commit"],
    })
    dino = ROOT / "benchmark_artifacts/dinov2"
    dc, ds = read(dino / "experiment_config.json"), read(dino / "run_summary.json")
    rows.append({
        "id": "dinov2_vits14", "model": "DINOv2 ViT-S/14", "status": "historical_experimental",
        "method": "frozen backbone + linear probe (" + ds["selected_probe"] + ")",
        "input_height": 224, "input_width": 224, "parameters": dc["total_parameters"],
        **metrics_fields(read(dino / "validation_metrics.json"), read(dino / "test_metrics.json")),
        "temperature": ds["temperature"], "threshold": ds["threshold"],
        "test_coverage": ds["coverage"], "test_accepted_accuracy": ds["accepted_accuracy"],
        "artifact_format": "PyTorch checkpoint + NumPy probe",
        "artifact_bytes": dc["checkpoint_bytes"] + (dino / "classifier.npz").stat().st_size,
        "desktop_median_ms": ds["desktop_single_image_ms_median"],
        "desktop_p95_ms": ds["desktop_single_image_ms_p95"], "timing_device": "GTX 1050 Ti (historical)",
        "source": "benchmark_artifacts/dinov2/",
    })
    environment = read(OUTPUT / "environment.json")
    for folder in sorted(p for p in OUTPUT.iterdir() if p.is_dir()):
        summary = read(folder / "run_summary.json")
        row = {"id": summary["model_id"], "model": summary["name"], "status": summary["status"],
               "source": "benchmark_artifacts/vision/" + folder.name + "/"}
        if summary["status"] == "completed":
            config = read(folder / "experiment_config.json")
            timing = config["timing"]
            row.update({
                "method": "frozen backbone + linear probe (" + summary["selected_probe"] + ")",
                "input_height": config["input_shape"][-2], "input_width": config["input_shape"][-1],
                "parameters": config["backbone_parameters"] + config["trainable_parameters"],
                **metrics_fields(summary["validation"], summary["test"]),
                "temperature": config["temperature"], "threshold": config["threshold"],
                "test_coverage": summary["test"]["coverage"],
                "test_accepted_accuracy": summary["test"]["accepted_accuracy"],
                "artifact_format": "image-only Safetensors + NumPy probe (not mobile export)",
                "artifact_bytes": config["image_encoder_bytes"] + config["classifier_bytes"],
                "desktop_median_ms": timing["desktop_single_image_ms_median"],
                "desktop_p95_ms": timing["desktop_single_image_ms_p95"],
                "timing_device": environment["gpu"] or environment["device"],
                "peak_cuda_batch1_bytes": timing["peak_cuda_allocated_bytes_batch1"],
            })
        rows.append(row)
    # Publish by validation only; test measurements never determine table ranking.
    return sorted(rows, key=lambda r: r.get("validation_macro_f1", -1), reverse=True)


def main():
    rows = build_rows()
    with (OUTPUT / "comparison.csv").open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=FIELDS, lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)
    print(f"Recorded {len(rows)} configurations in {OUTPUT / 'comparison.csv'}")


if __name__ == "__main__":
    main()
