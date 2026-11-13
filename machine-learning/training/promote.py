"""Automatic recommendation and explicit promotion of a cycle's candidate into the app.

Promotion replaces the model in artifacts/ and in the Android assets, writes the
metadata the app checks and an aggregate report in docs/model-reports/<name>/
(no per-image rows). It only happens on an explicit command.
"""
from __future__ import annotations

import shutil
from pathlib import Path

from leafcare.common import ROOT, classes_hash, read_json, sha256, write_json
from leafcare.preprocessing import DISTILLED_RESIZE

SUMMARY = ("top1_accuracy", "macro_f1", "top3_accuracy", "coverage", "accepted_accuracy")
ASSETS = ROOT.parent / "android-app/app/src/main/assets"
REPORTS = ROOT.parent / "docs/model-reports"
REPORT_FILES = ("comparison.json", "mobile_export.json", "history.json", "probe_comparison.json", "teacher.json",
                "per_class_metrics.csv", "reference_prediction.json")


def recommendation(candidate: dict, production: dict, max_drop: float, smoke: bool = False) -> dict:
    """Recommend only with field evidence: TLA test Macro-F1 may drop at most ``max_drop``, field Top-1 must not drop."""
    comparison = {split: {k: {"production": production[split][k], "candidate": candidate[split][k]} for k in SUMMARY}
                  for split in ("test", "field_test") if split in candidate and split in production}
    reasons = []
    if smoke:
        reasons.append("Execução de fumaça: nunca promovível.")
    tla = comparison.get("test")
    if not tla:
        reasons.append("Sem teste TLA no ciclo.")
    elif tla["macro_f1"]["candidate"] < tla["macro_f1"]["production"] - max_drop:
        reasons.append(f"Macro-F1 no teste TLA caiu mais de {max_drop}.")
    field = comparison.get("field_test")
    if not field:
        reasons.append("Sem teste de campo: não há evidência de melhora em fotos reais do app.")
    elif field["top1_accuracy"]["candidate"] < field["top1_accuracy"]["production"]:
        reasons.append("Top-1 nas fotos de campo ficou abaixo do modelo atual.")
    return {"comparison": comparison, "recommended_for_promotion": not reasons, "reasons": reasons,
            "criteria": {"max_tla_macro_f1_drop": max_drop, "field_top1": "candidate >= production",
                         "field_test_required": True},
            "note": "Recomendação automática; a promoção ao app exige decisão explícita e os testes Android."}


def install(run: Path, name: str, allow_unrecommended: bool = False, artifacts: Path = ROOT / "artifacts",
            assets: Path = ASSETS, reports: Path = REPORTS, allow_smoke: bool = False) -> dict:
    """Put the replayed candidate in the app. ``allow_smoke`` exists only for the pipeline smoke test."""
    config = read_json(run / "experiment_config.json")
    summary = read_json(run / "run_summary.json")
    export = read_json(run / "mobile_export.json")
    comparison = read_json(run / "comparison.json")
    replayed = read_json(run / "replay.json")["candidate"]
    model = run / export["path"]
    if summary.get("status") != "completed" or export.get("status") != "completed" or sha256(model) != export["sha256"]:
        raise ValueError("Treino, exportação ou replay incompletos, ou o TFLite mudou depois da exportação.")
    if any(m["model_sha256"] != export["sha256"] for m in replayed.values()) or "test" not in replayed:
        raise ValueError("O replay não corresponde ao TFLite exportado; rode o ciclo de novo.")
    if summary.get("smoke_test") and not allow_smoke:
        raise ValueError("Execução de fumaça nunca é promovida.")
    if not comparison["recommended_for_promotion"] and not allow_unrecommended:
        raise ValueError("Candidato não recomendado: " + " ".join(comparison["reasons"]) +
                         " Use --allow-unrecommended só com decisão registrada da equipe.")
    dataset = read_json(run / "dataset.json")
    classes = config["class_order"]
    report = reports / name
    report.mkdir(parents=True, exist_ok=True)
    for split, metrics in replayed.items():
        write_json(report / f"{split}_metrics.json", metrics)
    for file in REPORT_FILES:
        shutil.copyfile(run / file, report / file)
    write_json(report / "dataset_summary.json", {k: dataset[k] for k in (
        "created_at", "seed", "base_manifest_sha256", "mirror_synced_at", "field_ratios", "field_groups", "counts",
        "split_sizes", "field_counts", "rejected", "warnings")})
    metadata = {
        "schema_version": 2, "status": "trained", "architecture": "MobileNetV4SmallDistilled",
        "classes": classes, "classes_sha256": classes_hash(classes), "model_sha256": export["sha256"],
        "input_shape": [1, 224, 224, 3], "input_dtype": "float32", "output_shape": [1, len(classes)], "output_dtype": "float32",
        "resize": DISTILLED_RESIZE, "normalization": "embedded_imagenet_mean_std", "input_range": [0, 255],
        "max_image_pixels": 16000000,
        "preprocessing": {**config["preprocessing"], "short_side": 256, "center_crop": 224, "coefficient_precision_bits": 22},
        "temperature": config["temperature"], "confidence_threshold": config["threshold"], "threshold_calibrated": True,
        "probability_calibration": "embedded_temperature_scaling", "parameters": config["parameters"],
        "checkpoint_sha256": config["checkpoint_sha256"], "manifest_sha256": config["base_manifest_sha256"],
        "dataset_sha256": config["dataset_sha256"],
        "knowledge_distillation": {"teacher": config["teacher"]["spec"]["name"],
                                   "teacher_checkpoint_sha256": config["teacher"]["checkpoint_sha256"],
                                   "temperature": config["recipe"]["distillation_temperature"],
                                   "weight": config["recipe"]["distillation_weight"]},
        "source_experiment": f"runs/{run.name}", "report": f"docs/model-reports/{name}",
        "selected_epoch": config["selected_epoch"], "conversion_parity": export["parity"], "test_used_for_selection": False,
    }
    for folder in (artifacts, assets):
        shutil.copyfile(model, folder / "leafcare.tflite")
        write_json(folder / "model_metadata.json", metadata)
    write_json(artifacts / "training_metadata.json", {**config, "architecture": "MobileNetV4SmallDistilled",
                                                      "model_sha256": export["sha256"]})
    write_json(artifacts / "metrics.json", {**replayed["test"], "status": "evaluated", "inference_backend": "TFLite float32",
                                            "classes": classes, "accuracy": replayed["test"]["top1_accuracy"],
                                            "threshold_calibrated": True})
    return metadata
