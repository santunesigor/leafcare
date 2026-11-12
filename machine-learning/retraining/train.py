"""Train a candidate MobileNetV4 Small with the LeafCare recipe on one retraining cycle.

Same recipe as the model in the app (benchmark_artifacts/distillation): ImageNet
weights and a logistic probe as the starting head, 3 head epochs and up to 15
encoder epochs (patience 5), AdamW, batch 8, frozen BatchNorm, balanced cross
entropy plus knowledge distillation from the DINOv2-B teacher (T=4, weight 0.5).
The checkpoint is chosen by validation Macro-F1/Top-1/Top-3; temperature and
threshold are fitted on validation; only then are the TLA test and the field test
evaluated. Without ``--teacher`` the run is supervised only and is recorded as such.

Nothing here touches the app: the output is a candidate in runs/<cycle>/.
"""
from __future__ import annotations

import argparse
import gc
import json
import platform
import random
import time
from collections import Counter
from datetime import datetime, timezone
from importlib.metadata import distributions
from pathlib import Path

import numpy as np
import timm
import torch
from PIL import Image
from safetensors.torch import load_file, save_file
from scipy.special import softmax
from torch.utils.data import Dataset

from experiments.dinov2.benchmark import (
    classification_metrics, digest, encode_labels, fit_temperature, save_class_metrics, save_confusion,
    save_predictions, select_threshold,
)
from experiments.distillation import benchmark as recipe
from experiments.vision.benchmark import evaluated_metrics, fit_probes
from experiments.vision.finetune import Classifier, validation_key
from leafcare.common import read_json, sha256, write_json
from leafcare.preprocessing import decode_rgb
from retraining.dataset import SCHEMA, export_is_current

ROOT = Path(__file__).resolve().parents[1]
STUDENT = "mobilenetv4_small"
TEACHER_CONFIG = ROOT / "benchmark_artifacts/vision_finetune/dinov2_vitb14/experiment_config.json"
PRODUCTION_METRICS = ROOT / "artifacts/metrics.json"
PRODUCTION_METADATA = ROOT / "artifacts/model_metadata.json"


class RunImages(Dataset):
    """Rows of one split; TLA and field images resolved from their own roots."""

    def __init__(self, rows, roots, classes, transform, epoch=0):
        self.rows, self.roots, self.transform, self.epoch = rows, roots, transform, epoch
        self.labels = encode_labels(rows, classes)

    def __len__(self):
        return len(self.rows)

    def __getitem__(self, index):
        row = self.rows[index]
        image = Image.fromarray(decode_rgb(self.roots[row["source"]] / row["path"]))
        if self.epoch:
            image = recipe.augmented(image, self.epoch, index)
        return self.transform(image), int(self.labels[index]), index


def resolve(path: str) -> Path:
    candidate = Path(path)
    return candidate if candidate.is_absolute() else ROOT / candidate


def load_dataset(path: Path, now: datetime):
    dataset = read_json(path)
    if dataset.get("schema") != SCHEMA:
        raise ValueError("dataset.json não foi gerado por retraining.dataset.")
    if dataset["classes"] != read_json(ROOT / "artifacts/classes.json"):
        raise ValueError("A ordem das classes difere do modelo do app; o contrato de 16 classes não pode mudar aqui.")
    field_rows = [r for r in dataset["rows"] if r["source"] == "field"]
    if field_rows and not export_is_current(dataset["field_export"], now):
        raise ValueError("A exportação de campo venceu (30 dias); exporte e monte o ciclo de novo.")
    roots = {name: resolve(value) for name, value in dataset["roots"].items()}
    missing = [r["path"] for r in dataset["rows"]
               if not (roots[r["source"]] / r["path"]).is_file() or sha256(roots[r["source"]] / r["path"]) != r["sha256"]]
    if missing:
        raise FileNotFoundError(f"{len(missing)} imagens ausentes ou alteradas, por exemplo {missing[:3]}. "
                                "O TLA fica em data/raw (import_tla.py) e as fotos de campo na exportação.")
    return dataset, roots


def load_teacher(path: Path, classes):
    config = read_json(TEACHER_CONFIG)
    teacher = Classifier(recipe.DinoTokens(), np.zeros((len(classes), 1536)), np.zeros(len(classes)))
    teacher.load_state_dict(load_file(str(path)), strict=True)
    teacher.eval().requires_grad_(False)
    transform = timm.data.create_transform(**config["preprocessing"], is_training=False)
    checksum = digest(path)
    return teacher, transform, {"checkpoint_sha256": checksum, "matches_recorded_teacher": checksum == config["checkpoint_sha256"],
                                "recorded_teacher_sha256": config["checkpoint_sha256"]}


def teacher_targets(teacher_path, rows, roots, classes, device, epochs, out):
    teacher, transform, info = load_teacher(teacher_path, classes)
    teacher.to(device)
    targets = []
    for epoch in range(1, epochs + 1):
        # Same augmented view per (epoch, index) as the student will see.
        targets.append(recipe.evaluate(teacher, RunImages(rows, roots, classes, transform, epoch), device))
        print(json.dumps({"teacher_epoch": epoch, "of": epochs}), flush=True)
    targets = np.stack(targets)
    np.save(out / "teacher_targets.npy", targets, allow_pickle=False)
    write_json(out / "teacher.json", {**info, "shape": list(targets.shape),
                                      "logits": "raw, softened with T=4 only in the KD loss", "only_train_targets": True})
    del teacher
    gc.collect()
    if device.type == "cuda":
        torch.cuda.empty_cache()
    return targets, info


def production_comparison(dataset, results):
    """Candidate against the model in the app, on the same evidence."""
    current = read_json(PRODUCTION_METRICS) if PRODUCTION_METRICS.exists() else {}
    metadata = read_json(PRODUCTION_METADATA) if PRODUCTION_METADATA.exists() else {}
    same_tla_split = dataset["base_manifest_sha256"] == metadata.get("manifest_sha256")
    comparison = {"production_model_sha256": metadata.get("model_sha256"), "same_tla_test": same_tla_split}
    if same_tla_split and current:
        comparison["tla_test"] = {k: {"production": current.get(k), "candidate": results["test"][k]}
                                  for k in ("top1_accuracy", "macro_f1", "top3_accuracy", "coverage", "accepted_accuracy")}
    baseline = dataset.get("production_model_on_field_test")
    if baseline and "field_test" in results:
        comparison["field_test_top1"] = {"production": baseline["top1_accuracy"], "candidate": results["field_test"]["top1_accuracy"],
                                         "n_images": baseline["n_images"]}
    reasons = []
    tla = comparison.get("tla_test")
    if tla and tla["macro_f1"]["candidate"] < tla["macro_f1"]["production"] - 0.02:
        reasons.append("Macro-F1 no teste TLA caiu mais de 0,02.")
    field = comparison.get("field_test_top1")
    if field and field["candidate"] < field["production"]:
        reasons.append("Top-1 nas fotos de campo ficou abaixo do modelo atual.")
    if not field:
        reasons.append("Sem teste de campo: não há evidência de melhora em fotos reais do app.")
    comparison["recommended_for_promotion"] = not reasons
    comparison["reasons"] = reasons
    comparison["note"] = "Recomendação automática; a promoção ao app exige decisão explícita e os testes Android."
    return comparison


def train(dataset_path: Path, teacher_path: Path | None, warmup_epochs: int, finetune_epochs: int, smoke: bool, device_name: str | None):
    now = datetime.now(timezone.utc)
    out = dataset_path.parent
    if (out / "run_summary.json").exists() and read_json(out / "run_summary.json").get("status") == "completed":
        raise SystemExit(f"{out} já tem um treino concluído; monte um novo ciclo para treinar de novo.")
    dataset, roots = load_dataset(dataset_path, now)
    classes = dataset["classes"]
    rows = {split: [r for r in dataset["rows"] if r["split"] == split] for split in ("train", "validation", "test", "field_test")}
    if len({r["class_id"] for r in rows["train"]}) != len(classes):
        raise ValueError("O treino precisa de ao menos uma imagem de cada uma das 16 classes.")
    write_json(out / "run_summary.json", {"status": "running", "started_at": now.isoformat()})
    random.seed(recipe.SEED)
    np.random.seed(recipe.SEED)
    torch.manual_seed(recipe.SEED)
    torch.backends.cudnn.deterministic = True
    torch.backends.cudnn.benchmark = False
    device = torch.device(device_name or ("cuda" if torch.cuda.is_available() else "cpu"))
    epochs = warmup_epochs + finetune_epochs
    method = "distilled" if teacher_path else "supervised"
    teacher_info = None
    targets = None
    if teacher_path:
        targets, teacher_info = teacher_targets(teacher_path, rows["train"], roots, classes, device, epochs, out)

    encoder, transform, preprocessing, pretrained = recipe.load_student(STUDENT)
    encoder.to(device).eval().requires_grad_(False)
    train_set = RunImages(rows["train"], roots, classes, transform)
    val_set = RunImages(rows["validation"], roots, classes, transform)
    train_x, val_x = [recipe.evaluate(encoder, images, device) for images in (train_set, val_set)]
    probe, _, candidates, selected_probe = fit_probes(train_x, train_set.labels, val_x, val_set.labels, classes)
    np.savez_compressed(out / "initial_probe.npz", coef=probe.coef_, intercept=probe.intercept_, classes=classes)
    write_json(out / "probe_comparison.json", {"models": candidates, "selected": selected_probe, "test_used": False})
    model = Classifier(encoder, probe.coef_, probe.intercept_).to(device)

    counts = Counter(train_set.labels)
    weights = torch.tensor([len(train_set) / (len(classes) * counts[i]) for i in range(len(classes))], device=device, dtype=torch.float32)
    checkpoint = out / "checkpoint.safetensors"

    def save_best():
        save_file({k: v.detach().cpu().contiguous() for k, v in model.state_dict().items()}, str(checkpoint))

    metrics = classification_metrics(val_set.labels, recipe.evaluate(model, val_set, device), classes)
    best_key, best_epoch, best_stage = validation_key(metrics), 0, "initial_probe"
    save_best()
    history = [{"epoch": 0, "stage": best_stage, "validation": {k: metrics[k] for k in ("macro_f1", "top1_accuracy", "top3_accuracy")}}]
    started, epoch = time.perf_counter(), 0
    for stage, stage_epochs in (("head_warmup", warmup_epochs), ("finetune", finetune_epochs)):
        model.encoder.requires_grad_(stage == "finetune")
        model.head.requires_grad_(True)
        groups = [{"params": model.head.parameters(),
                   "lr": recipe.RECIPE["head_warmup_lr"] if stage == "head_warmup" else recipe.RECIPE["head_finetune_lr"]}]
        if stage == "finetune":
            groups.append({"params": model.encoder.parameters(), "lr": recipe.RECIPE["backbone_lr"]})
        optimizer = torch.optim.AdamW(groups, weight_decay=recipe.RECIPE["weight_decay"])
        stale = 0
        for _ in range(stage_epochs):
            epoch += 1
            tick = time.perf_counter()
            train_set.epoch = epoch
            model.train()
            if stage == "head_warmup":
                model.encoder.eval()
            for module in model.modules():
                if isinstance(module, torch.nn.modules.batchnorm._BatchNorm):
                    module.eval()
            losses = np.zeros(3)
            for images, labels, indices in recipe.loader(train_set, True, epoch):
                optimizer.zero_grad(set_to_none=True)
                scores = model(images.to(device))
                teacher_scores = torch.from_numpy(targets[epoch - 1, indices.numpy()]).to(device) if targets is not None else None
                loss, ce, kl = recipe.kd_loss(scores, teacher_scores, labels.to(device), weights, targets is not None)
                if not torch.isfinite(loss):
                    raise ValueError("Perda de treino não finita.")
                loss.backward()
                optimizer.step()
                losses += np.array([float(loss.detach()), float(ce), float(kl)]) * len(labels)
            metrics = classification_metrics(val_set.labels, recipe.evaluate(model, val_set, device), classes)
            improved = validation_key(metrics) > best_key
            if improved:
                best_key, best_epoch, best_stage = validation_key(metrics), epoch, stage
                save_best()
                stale = 0
            else:
                stale += 1
            record = {"epoch": epoch, "stage": stage, "selected": improved, "training_loss": float(losses[0] / len(train_set)),
                      "training_ce": float(losses[1] / len(train_set)), "training_kl_T2": float(losses[2] / len(train_set)),
                      "seconds": time.perf_counter() - tick,
                      "validation": {k: metrics[k] for k in ("macro_f1", "top1_accuracy", "top3_accuracy")}}
            history.append(record)
            write_json(out / "history.json", history)
            print(json.dumps(record), flush=True)
            if stage == "finetune" and stale >= recipe.RECIPE["patience"]:
                break
        del optimizer

    model.load_state_dict(load_file(str(checkpoint)), strict=True)
    recipe.clear_attention_cache(model)
    model.eval().requires_grad_(False)
    val_logits = recipe.evaluate(model, val_set, device)
    temperature, calibration = fit_temperature(val_set.labels, val_logits)
    threshold, threshold_info = select_threshold(val_set.labels, softmax(val_logits / temperature, axis=1))
    results, logits = {}, {"validation": val_logits}
    results["validation"], _ = evaluated_metrics(val_set.labels, val_logits, classes, temperature, threshold)
    configuration = {
        **recipe.MODELS[STUDENT], "model_id": f"{STUDENT}_{method}", "base_model_id": STUDENT, "method": method,
        "recipe": {**recipe.RECIPE, "warmup_epochs": warmup_epochs, "finetune_epochs": finetune_epochs},
        "smoke_test": smoke, "selected_epoch": best_epoch, "selected_stage": best_stage, "epochs_executed": epoch,
        "training_seconds": time.perf_counter() - started, "class_order": classes,
        "dataset_sha256": digest(dataset_path), "base_manifest_sha256": dataset["base_manifest_sha256"],
        "split_sizes": {k: len(v) for k, v in rows.items()}, "pretrained_sha256": digest(pretrained),
        "initial_probe_sha256": digest(out / "initial_probe.npz"),
        "checkpoint": checkpoint.name, "checkpoint_sha256": digest(checkpoint), "checkpoint_bytes": checkpoint.stat().st_size,
        "parameters": sum(p.numel() for p in model.parameters()), "preprocessing": preprocessing,
        "input_shape": [1, 3, 224, 224], "temperature": temperature, "threshold": threshold,
        "test_used_for_selection": False, "teacher": teacher_info,
        "environment": {"python": platform.python_version(), "torch": torch.__version__, "device": str(device),
                        "gpu": torch.cuda.get_device_name(0) if device.type == "cuda" else None},
    }
    write_json(out / "experiment_config.json", configuration)
    write_json(out / "validation_metrics.json", {**results["validation"], "calibration": calibration, "threshold_selection": threshold_info})
    # Held-out splits are decoded only after selection, temperature and threshold are persisted.
    for split in ("test", "field_test"):
        if not rows[split]:
            continue
        images = RunImages(rows[split], roots, classes, transform)
        logits[split] = recipe.evaluate(model, images, device)
        results[split], probabilities = evaluated_metrics(images.labels, logits[split], classes, temperature, threshold)
        write_json(out / f"{split}_metrics.json", {**results[split], "test_used_for_selection": False})
        save_confusion(out / f"confusion_matrix_{split}.csv", results[split]["confusion_matrix"], classes)
        save_predictions(out / f"predictions_{split}.csv", rows[split], images.labels, logits[split], probabilities, threshold, classes)
    np.savez_compressed(out / "logits.npz", **logits)
    save_class_metrics(out / "per_class_metrics.csv", results, classes)
    comparison = production_comparison(dataset, results)
    write_json(out / "comparison.json", comparison)
    summary = {"status": "completed", "finished_at": datetime.now(timezone.utc).isoformat(), "method": method,
               "smoke_test": smoke, "promotable": not smoke and method == "distilled",
               "selected_epoch": best_epoch,
               **{split: {k: results[split][k] for k in ("top1_accuracy", "macro_f1", "top3_accuracy", "coverage", "accepted_accuracy")}
                  for split in results},
               "recommended_for_promotion": comparison["recommended_for_promotion"] and not smoke,
               "reasons": comparison["reasons"]}
    write_json(out / "run_summary.json", summary)
    write_json(out / "environment.json", {"installed_packages": sorted(f"{p.metadata['Name']}=={p.version}" for p in distributions())})
    print(json.dumps(summary, ensure_ascii=False), flush=True)
    return summary


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--run", required=True, help="Ciclo criado por retraining.dataset (runs/<ciclo>/dataset.json).")
    parser.add_argument("--teacher", type=Path, help="Checkpoint DINOv2-B (safetensors). Sem ele, o treino é só supervisionado.")
    parser.add_argument("--device", choices=["cpu", "cuda"])
    parser.add_argument("--smoke", action="store_true", help="Teste rápido do pipeline (1+1 épocas); nunca promovível.")
    args = parser.parse_args()
    warmup, finetune = (1, 1) if args.smoke else (recipe.RECIPE["warmup_epochs"], recipe.RECIPE["finetune_epochs"])
    torch.set_num_threads(2)
    train(ROOT / "runs" / args.run / "dataset.json", args.teacher, warmup, finetune, args.smoke, args.device)


if __name__ == "__main__":
    main()
