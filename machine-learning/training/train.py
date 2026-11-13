"""Teacher and student training for one retraining cycle (training environment).

Teacher: DINOv2 ViT-B/14 (CLS + mean patch tokens), validation-selected logistic
probe as the initial head, 3 head epochs, then up to 15 epochs fine-tuning the last
two blocks and the final norm (patience 5). It is trained on the cycle's train and
validation rows and cached in .cache/teachers/<identity>/, so rerunning the same
cycle reuses it.

Student (the app model): MobileNetV4 Conv Small with ImageNet weights and the same
probe initialization and schedule, the whole encoder fine-tuned, balanced cross
entropy plus knowledge distillation from the teacher (T=4, weight 0.5). Both use
AdamW, batch 8 (fits a 4 GB GTX 1650), frozen BatchNorm statistics, the same
seeded augmentation, and keep the checkpoint with the best validation
Macro-F1/Top-1/Top-3. Temperature and threshold are fitted on validation; only then
are the TLA test and the field test evaluated.
"""
from __future__ import annotations

import gc
import hashlib
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
from huggingface_hub import hf_hub_download
from PIL import Image
from safetensors.torch import load_file, save_file
from scipy.special import softmax
from torch.utils.data import DataLoader, Dataset
from torchvision import transforms
from torchvision.transforms import InterpolationMode

from leafcare.common import ROOT, read_json, sha256, write_json
from leafcare.preprocessing import decode_rgb
from training.dataset import SPLITS, load_dataset, split_rows
from training.metrics import (
    SEED, classification_metrics, encode_labels, evaluated_metrics, fit_probes, fit_temperature,
    save_class_metrics, save_confusion, save_predictions, select_threshold, validation_key,
)
from training.promote import SUMMARY

WEIGHTS = ROOT / ".cache/weights"
TEACHERS = ROOT / ".cache/teachers"
RECIPE = {
    "seed": SEED, "batch_size": 8, "warmup_epochs": 3, "finetune_epochs": 15, "patience": 5,
    "optimizer": "AdamW", "head_warmup_lr": 1e-3, "head_finetune_lr": 1e-4, "backbone_lr": 1e-5, "weight_decay": 1e-4,
    "class_weight": "balanced from training counts only",
    "distillation_temperature": 4.0, "distillation_weight": 0.5,
    "loss": "student: (1-alpha) * weighted CE + alpha * T^2 * batchmean KL(teacher || student); teacher: weighted CE",
    "augmentation": "flip H/V p=.5; rotation +/-12 bilinear white fill; brightness/contrast .1 saturation .05; "
                    "seed=42+epoch*100000+train_index",
    "batch_norm": "running statistics frozen",
    "initialization": "validation-selected C=1 logistic probe on frozen features",
    "selection": "validation Macro-F1, Top-1, Top-3; earliest on ties",
    "precision": "float32",
}
PREPROCESSING = {"input_size": [3, 224, 224], "interpolation": "bicubic", "mean": [0.485, 0.456, 0.406],
                 "std": [0.229, 0.224, 0.225], "crop_pct": 0.875, "crop_mode": "center"}
TEACHER = {
    "name": "DINOv2 ViT-B/14 partial fine-tuning", "architecture": "vit_base_patch14_dinov2.lvd142m",
    "revision": "4685c99dabffe5affac90bd99dbffd25801ae58d",
    "pretrained_sha256": "55cbb5d887b336d430e649c277b85a1429e724871f9d02ac16203235886d8c7b",
    "feature": "CLS + mean patch tokens", "trainable": "last 2 blocks + final norm", "trainable_blocks": 2,
    "preprocessing": PREPROCESSING,
}
STUDENT = {
    "name": "MobileNetV4 Conv Small", "architecture": "mobilenetv4_conv_small.e2400_r224_in1k",
    "revision": "331fb803779522b685cf942e15f914fb6741c1eb",
    "pretrained_sha256": "7a7102ec18f62bbfb555b6fe829bbb5af749516b84174926c29ffdfdfc03aec4",
}
AUGMENT = transforms.Compose([
    transforms.RandomHorizontalFlip(), transforms.RandomVerticalFlip(),
    transforms.RandomRotation(12, interpolation=InterpolationMode.BILINEAR, fill=255),
    transforms.ColorJitter(brightness=.1, contrast=.1, saturation=.05),
])


def seed_everything():
    random.seed(SEED)
    np.random.seed(SEED)
    torch.manual_seed(SEED)
    torch.backends.cudnn.deterministic = True
    torch.backends.cudnn.benchmark = False


def release(device):
    gc.collect()
    if device.type == "cuda":
        torch.cuda.empty_cache()


def pretrained_weights(spec) -> Path:
    path = Path(hf_hub_download("timm/" + spec["architecture"], "model.safetensors", revision=spec["revision"],
                                cache_dir=str(WEIGHTS)))
    if sha256(path) != spec["pretrained_sha256"]:
        raise ValueError(f"Pesos pré-treinados de {spec['architecture']} diferentes dos registrados.")
    return path


def augmented(image, epoch, index):
    # Same PIL view for teacher and student, without touching the loader RNG.
    with torch.random.fork_rng(devices=[]):
        torch.random.default_generator.manual_seed(SEED + epoch * 100000 + index)
        return AUGMENT(image)


class Images(Dataset):
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
            image = augmented(image, self.epoch, index)
        return self.transform(image), int(self.labels[index]), index


def loader(images, shuffle=False, epoch=0):
    return DataLoader(images, batch_size=RECIPE["batch_size"], shuffle=shuffle,
                      generator=torch.Generator().manual_seed(SEED + epoch), num_workers=0)


def evaluate(model, images, device):
    model.eval()
    with torch.inference_mode():
        return np.concatenate([model(batch.to(device)).float().cpu().numpy() for batch, _, _ in loader(images)])


def clear_attention_cache(model):
    for module in model.modules():
        if hasattr(module, "attention_bias_cache"):
            module.attention_bias_cache.clear()


class Classifier(torch.nn.Module):
    def __init__(self, encoder, coef, intercept):
        super().__init__()
        self.encoder = encoder
        self.head = torch.nn.Linear(coef.shape[1], coef.shape[0])
        with torch.no_grad():
            self.head.weight.copy_(torch.as_tensor(coef, dtype=torch.float32))
            self.head.bias.copy_(torch.as_tensor(intercept, dtype=torch.float32))

    def forward(self, images):
        return self.head(self.encoder(images))


class TokenEncoder(torch.nn.Module):
    """ViT features: CLS token concatenated with the mean patch token."""

    def __init__(self, spec, pretrained):
        super().__init__()
        self.backbone = timm.create_model(spec["architecture"], pretrained=False)
        if pretrained:
            self.backbone.load_state_dict(load_file(str(pretrained_weights(spec))), strict=True)
        self.backbone.set_input_size(img_size=224)  # historical 224 px input, not the 518 px pretraining
        self.backbone.reset_classifier(0)
        self.dim = 2 * self.backbone.embed_dim

    def forward(self, images):
        tokens = self.backbone.forward_features(images)
        return torch.cat((tokens[:, 0], tokens[:, self.backbone.num_prefix_tokens:].mean(dim=1)), dim=1)


def load_student(pretrained=True):
    encoder = timm.create_model(STUDENT["architecture"], pretrained=False)
    if pretrained:
        encoder.load_state_dict(load_file(str(pretrained_weights(STUDENT))), strict=True)
    encoder.reset_classifier(0)
    preprocessing = timm.data.resolve_model_data_config(encoder)
    return encoder, timm.data.create_transform(**preprocessing, is_training=False), preprocessing


def load_teacher(path: Path, spec: dict, n_classes: int):
    encoder = TokenEncoder(spec, pretrained=False)
    teacher = Classifier(encoder, np.zeros((n_classes, encoder.dim)), np.zeros(n_classes))
    teacher.load_state_dict(load_file(str(path)), strict=True)
    return teacher.eval().requires_grad_(False)


def with_probe(encoder, train_set, val_set, classes, device, out: Path):
    """Initial head: the better of two logistic probes on frozen, non-augmented features."""
    encoder.to(device).eval().requires_grad_(False)
    probe, candidates, selected = fit_probes(evaluate(encoder, train_set, device), train_set.labels,
                                             evaluate(encoder, val_set, device), val_set.labels, classes)
    np.savez_compressed(out / "initial_probe.npz", coef=probe.coef_, intercept=probe.intercept_, classes=classes)
    write_json(out / "probe_comparison.json", {"models": candidates, "selected": selected, "test_used": False})
    return Classifier(encoder, probe.coef_, probe.intercept_).to(device)


def all_parameters(encoder):
    encoder.requires_grad_(True)
    return list(encoder.parameters())


def last_blocks(count):
    def select(encoder):
        for block in encoder.backbone.blocks[-count:]:
            block.requires_grad_(True)
        encoder.backbone.norm.requires_grad_(True)
        return [p for p in encoder.parameters() if p.requires_grad]
    return select


def kd_loss(scores, teacher_scores, labels, weights):
    ce = torch.nn.functional.cross_entropy(scores, labels, weight=weights)
    if teacher_scores is None:
        return ce, ce.detach(), torch.zeros((), device=scores.device)
    temperature, alpha = RECIPE["distillation_temperature"], RECIPE["distillation_weight"]
    kl = torch.nn.functional.kl_div(torch.log_softmax(scores / temperature, dim=1),
                                    torch.softmax(teacher_scores.detach() / temperature, dim=1),
                                    reduction="batchmean") * temperature**2
    return (1 - alpha) * ce + alpha * kl, ce.detach(), kl.detach()


def fit(model, trainable, train_set, val_set, classes, device, checkpoint: Path, history_path: Path, epochs,
        targets=None):
    """Head warmup, then fine-tuning with early stopping; restores the best validation checkpoint."""
    counts = Counter(train_set.labels.tolist())
    weights = torch.tensor([len(train_set) / (len(classes) * counts[i]) for i in range(len(classes))],
                           device=device, dtype=torch.float32)

    def save_best():
        save_file({k: v.detach().cpu().contiguous() for k, v in model.state_dict().items()}, str(checkpoint))

    metrics = classification_metrics(val_set.labels, evaluate(model, val_set, device), classes)
    best_key, best_epoch, best_stage = validation_key(metrics), 0, "initial_probe"
    save_best()
    history = [{"epoch": 0, "stage": best_stage, "validation": dict(zip(("macro_f1", "top1_accuracy", "top3_accuracy"), best_key))}]
    started, epoch = time.perf_counter(), 0
    for stage, stage_epochs in zip(("head_warmup", "finetune"), epochs):
        model.encoder.requires_grad_(False)
        groups = [{"params": model.head.parameters(),
                   "lr": RECIPE["head_warmup_lr"] if stage == "head_warmup" else RECIPE["head_finetune_lr"]}]
        if stage == "finetune":
            groups.append({"params": trainable(model.encoder), "lr": RECIPE["backbone_lr"]})
        model.head.requires_grad_(True)
        optimizer = torch.optim.AdamW(groups, weight_decay=RECIPE["weight_decay"])
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
            for images, labels, indices in loader(train_set, True, epoch):
                optimizer.zero_grad(set_to_none=True)
                scores = model(images.to(device))
                teacher_scores = None if targets is None else torch.from_numpy(targets[epoch - 1, indices.numpy()]).to(device)
                loss, ce, kl = kd_loss(scores, teacher_scores, labels.to(device), weights)
                if not torch.isfinite(loss):
                    raise ValueError("Perda de treino não finita.")
                loss.backward()
                optimizer.step()
                losses += np.array([float(loss.detach()), float(ce), float(kl)]) * len(labels)
            metrics = classification_metrics(val_set.labels, evaluate(model, val_set, device), classes)
            improved = validation_key(metrics) > best_key
            if improved:
                best_key, best_epoch, best_stage, stale = validation_key(metrics), epoch, stage, 0
                save_best()
            else:
                stale += 1
            record = {"epoch": epoch, "stage": stage, "selected": improved, "training_loss": losses[0] / len(train_set),
                      "training_ce": losses[1] / len(train_set), "training_kl_T2": losses[2] / len(train_set),
                      "seconds": time.perf_counter() - tick,
                      "validation": dict(zip(("macro_f1", "top1_accuracy", "top3_accuracy"), validation_key(metrics)))}
            history.append(record)
            write_json(history_path, history)
            print(json.dumps({"checkpoint": checkpoint.name, **record}), flush=True)
            if stage == "finetune" and stale >= RECIPE["patience"]:
                break
        del optimizer
    train_set.epoch = 0
    model.load_state_dict(load_file(str(checkpoint)), strict=True)
    clear_attention_cache(model)
    model.eval().requires_grad_(False)
    return {"selected_epoch": best_epoch, "selected_stage": best_stage, "epochs_executed": epoch,
            "training_seconds": time.perf_counter() - started, "best_validation_key": list(best_key)}


def teacher_identity(dataset, spec, epochs) -> str:
    rows = sorted((r["split"], r["source"], r["path"], r["sha256"], r["class_id"])
                  for r in dataset["rows"] if r["split"] in ("train", "validation"))
    payload = {"rows": rows, "classes": dataset["classes"], "spec": spec, "recipe": RECIPE, "epochs": list(epochs)}
    return hashlib.sha256(json.dumps(payload, sort_keys=True).encode()).hexdigest()


def train_teacher(dataset, roots, device, spec=TEACHER, epochs=None, cache: Path = TEACHERS) -> Path:
    """Fine-tune the teacher on this cycle's train/validation rows, or reuse the cached one."""
    epochs = epochs or (RECIPE["warmup_epochs"], RECIPE["finetune_epochs"])
    identity = teacher_identity(dataset, spec, epochs)
    folder = cache / identity[:16]
    checkpoint, info_path = folder / "teacher.safetensors", folder / "teacher.json"
    if info_path.is_file():
        info = read_json(info_path)
        if info.get("status") == "completed" and checkpoint.is_file() and sha256(checkpoint) == info["checkpoint_sha256"]:
            print(json.dumps({"teacher": "reused", "path": str(checkpoint)}), flush=True)
            return checkpoint
    folder.mkdir(parents=True, exist_ok=True)
    write_json(info_path, {"status": "running", "identity": identity})
    seed_everything()
    classes = dataset["classes"]
    transform = timm.data.create_transform(**spec["preprocessing"], is_training=False)
    train_set, val_set = (Images(split_rows(dataset, s), roots, classes, transform) for s in ("train", "validation"))
    model = with_probe(TokenEncoder(spec, pretrained=spec["pretrained_sha256"] is not None), train_set, val_set,
                       classes, device, folder)
    result = fit(model, last_blocks(spec["trainable_blocks"]), train_set, val_set, classes, device, checkpoint,
                 folder / "history.json", epochs)
    validation = classification_metrics(val_set.labels, evaluate(model, val_set, device), classes)
    write_json(info_path, {
        "status": "completed", "identity": identity, "trained_at": datetime.now(timezone.utc).isoformat(),
        "spec": spec, "recipe": RECIPE, "epochs": list(epochs), **result,
        "rows": {s: len(d) for s, d in (("train", train_set), ("validation", val_set))},
        "validation": {k: validation[k] for k in ("top1_accuracy", "macro_f1", "top3_accuracy")},
        "checkpoint_sha256": sha256(checkpoint), "test_used_for_selection": False,
    })
    del model
    release(device)
    return checkpoint


def teacher_targets(teacher_path, spec, rows, roots, classes, device, epochs):
    """Raw teacher logits for every augmented training view the student will see."""
    teacher = load_teacher(teacher_path, spec, len(classes)).to(device)
    transform = timm.data.create_transform(**spec["preprocessing"], is_training=False)
    targets = []
    for epoch in range(1, epochs + 1):
        targets.append(evaluate(teacher, Images(rows, roots, classes, transform, epoch), device))
        print(json.dumps({"teacher_targets_epoch": epoch, "of": epochs}), flush=True)
    targets = np.stack(targets)
    if not np.isfinite(targets).all():
        raise ValueError("Saídas do professor não finitas.")
    del teacher
    release(device)
    return targets


def train_student(run: Path, teacher_path: Path, teacher_spec: dict, device, epochs=None, smoke=False) -> dict:
    """Distill the app model for the cycle in ``run``; writes the candidate and its held-out metrics."""
    dataset, roots = load_dataset(run / "dataset.json")
    classes = dataset["classes"]
    rows = {split: split_rows(dataset, split) for split in SPLITS}
    if len({r["class_id"] for r in rows["train"]}) != len(classes):
        raise ValueError("O treino precisa de ao menos uma imagem de cada uma das 16 classes.")
    epochs = epochs or (RECIPE["warmup_epochs"], RECIPE["finetune_epochs"])
    write_json(run / "run_summary.json", {"status": "running", "started_at": datetime.now(timezone.utc).isoformat()})
    seed_everything()
    teacher_info = {"checkpoint_sha256": sha256(teacher_path), "spec": teacher_spec}
    if (teacher_path.parent / "teacher.json").is_file():
        recorded = read_json(teacher_path.parent / "teacher.json")
        teacher_info.update({k: recorded.get(k) for k in ("identity", "trained_at", "selected_epoch", "validation")})
    targets = teacher_targets(teacher_path, teacher_spec, rows["train"], roots, classes, device, sum(epochs))
    write_json(run / "teacher.json", {**teacher_info, "targets_shape": list(targets.shape),
                                      "logits": "raw, softened with T=4 only in the KD loss"})

    encoder, transform, preprocessing = load_student()
    train_set, val_set = (Images(rows[s], roots, classes, transform) for s in ("train", "validation"))
    model = with_probe(encoder, train_set, val_set, classes, device, run)
    checkpoint = run / "checkpoint.safetensors"
    result = fit(model, all_parameters, train_set, val_set, classes, device, checkpoint, run / "history.json", epochs, targets)
    val_logits = evaluate(model, val_set, device)
    if list(validation_key(classification_metrics(val_set.labels, val_logits, classes))) != result["best_validation_key"]:
        raise ValueError("A validação do checkpoint restaurado difere da época selecionada.")
    temperature, calibration = fit_temperature(val_set.labels, val_logits)
    threshold, threshold_info = select_threshold(val_set.labels, softmax(val_logits / temperature, axis=1))
    results, logits = {}, {"validation": val_logits}
    results["validation"], _ = evaluated_metrics(val_set.labels, val_logits, classes, temperature, threshold)
    configuration = {
        **STUDENT, "model_id": "mobilenetv4_small_distilled", "method": "distilled",
        "recipe": RECIPE, "epochs": list(epochs), "smoke_test": smoke, **result, "class_order": classes,
        "dataset_sha256": sha256(run / "dataset.json"), "base_manifest_sha256": dataset["base_manifest_sha256"],
        "split_sizes": {k: len(v) for k, v in rows.items()},
        "initial_probe_sha256": sha256(run / "initial_probe.npz"),
        "checkpoint": checkpoint.name, "checkpoint_sha256": sha256(checkpoint), "checkpoint_bytes": checkpoint.stat().st_size,
        "parameters": sum(p.numel() for p in model.parameters()), "preprocessing": preprocessing,
        "input_shape": [1, 3, 224, 224], "temperature": temperature, "threshold": threshold,
        "test_used_for_selection": False, "teacher": teacher_info,
        "environment": {"python": platform.python_version(), "torch": torch.__version__, "device": str(device),
                        "gpu": torch.cuda.get_device_name(0) if device.type == "cuda" else None},
    }
    write_json(run / "experiment_config.json", configuration)
    write_json(run / "validation_metrics.json", {**results["validation"], "calibration": calibration,
                                                  "threshold_selection": threshold_info})
    # Held-out splits are decoded only after selection, temperature and threshold are persisted.
    for split in ("test", "field_test"):
        if not rows[split]:
            continue
        images = Images(rows[split], roots, classes, transform)
        logits[split] = evaluate(model, images, device)
        results[split], probabilities = evaluated_metrics(images.labels, logits[split], classes, temperature, threshold)
        write_json(run / f"{split}_metrics.json", {**results[split], "test_used_for_selection": False})
        save_confusion(run / f"confusion_matrix_{split}.csv", results[split]["confusion_matrix"], classes)
        save_predictions(run / f"predictions_{split}.csv", rows[split], images.labels, probabilities, threshold, classes)
    np.savez_compressed(run / "logits.npz", **logits)
    save_class_metrics(run / "per_class_metrics.csv", results, classes)
    summary = {"status": "completed", "finished_at": datetime.now(timezone.utc).isoformat(), "smoke_test": smoke,
               "selected_epoch": result["selected_epoch"],
               **{split: {k: metrics[k] for k in SUMMARY} for split, metrics in results.items()}}
    write_json(run / "environment.json", {"installed_packages": sorted(f"{p.metadata['Name']}=={p.version}"
                                                                       for p in distributions())})
    write_json(run / "run_summary.json", summary)
    del model
    release(device)
    return summary
