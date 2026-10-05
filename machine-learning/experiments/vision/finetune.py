"""Controlled fine-tuning of five public candidates; Android bundle stays untouched."""
import argparse
import gc
import json
import platform
import statistics
import time
from collections import Counter
from importlib.metadata import distributions

import numpy as np
import torch
from PIL import Image
from safetensors.torch import load_file, save_file
from scipy.special import softmax
from torch.utils.data import DataLoader, Dataset
from torchvision import transforms
from torchvision.transforms import InterpolationMode

from experiments.vision.benchmark import (
    CACHE, MODELS, ROOT, SEED, EXPECTED_SPLITS, digest, evaluated_metrics,
    fit_temperature, load_encoder, manifest_digest, save_class_metrics,
    save_confusion, save_predictions, select_threshold, split_rows, validate_manifest,
)
from leafcare.common import load_config, write_json
from leafcare.dataset import load_manifest
from leafcare.preprocessing import decode_rgb

OUTPUT = ROOT / "benchmark_artifacts/vision_finetune"
PUBLIC_MODELS = [m for m in MODELS if m != "dinov3_vits16"]
RECIPE = {
    "seed": SEED, "batch_size": 8, "warmup_epochs": 3, "finetune_epochs": 15,
    "patience": 5, "head_warmup_lr": 1e-3, "head_finetune_lr": 1e-4,
    "backbone_lr": 1e-5, "weight_decay": 1e-4, "optimizer": "AdamW",
    "class_weight": "balanced from training counts only",
    "augmentation": "horizontal/vertical flip p=.5, bilinear rotation +/-12 degrees (white fill), brightness/contrast .1, saturation .05; official eval resize/crop/normalization follows",
    "batch_norm": "running statistics frozen during both stages",
    "scope": "all CNN/TinyViT parameters; DINOv2 last two blocks + final norm",
    "selection": "validation Macro-F1, Top-1, Top-3; keep earlier checkpoint on ties",
    "initialization": "selected historical linear probe copied to float32 Linear head",
}


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


def configure_trainable(model, model_id, finetune):
    model.encoder.requires_grad_(False)
    model.head.requires_grad_(True)
    if finetune:
        if model_id == "dinov2_vitb14":
            for block in model.encoder.backbone.blocks[-2:]:
                block.requires_grad_(True)
            model.encoder.backbone.norm.requires_grad_(True)
        else:
            model.encoder.requires_grad_(True)
    return sum(p.numel() for p in model.parameters() if p.requires_grad)


def validation_key(metrics):
    return tuple(metrics[k] for k in ("macro_f1", "top1_accuracy", "top3_accuracy"))


def run(model_id, config, manifest, classes, device):
    from experiments.dinov2.benchmark import classification_metrics
    folder = OUTPUT / model_id
    folder.mkdir(parents=True, exist_ok=True)
    write_json(folder / "run_summary.json", {"status": "running", "model_id": model_id})
    if (folder / "mobile_export.json").exists():
        write_json(folder / "mobile_export.json", {
            "status": "invalidated_by_training", "model_id": model_id,
            "experimental_only": True, "android_target_verified": False,
        })
    np.random.seed(SEED)
    torch.manual_seed(SEED)
    spec = MODELS[model_id]
    encoder, transform, preprocessing, pretrained, feature = load_encoder(spec, reparameterize=False)
    probe = ROOT / "benchmark_artifacts/vision" / model_id / "classifier.npz"
    previous_config = json.loads((probe.parent / "experiment_config.json").read_text())
    manifest_hash = manifest_digest(ROOT / config["prepared_dir"] / "manifest.json")
    if previous_config["manifest_sha256"] != manifest_hash:
        raise ValueError("Probe and current manifest differ")
    with np.load(probe, allow_pickle=False) as initial:
        if initial["classes"].tolist() != classes:
            raise ValueError("Probe class order changed")
        model = Classifier(encoder, initial["coef"], initial["intercept"]).to(device)
    augment = transforms.Compose([
        transforms.RandomHorizontalFlip(), transforms.RandomVerticalFlip(),
        transforms.RandomRotation(12, interpolation=InterpolationMode.BILINEAR, fill=255),
        transforms.ColorJitter(brightness=.1, contrast=.1, saturation=.05),
    ])

    class Images(Dataset):
        def __init__(self, split, training=False):
            self.rows = split_rows(manifest, split)
            self.training = training
            self.labels = [classes.index(r["class_id"]) for r in self.rows]

        def __len__(self):
            return len(self.rows)

        def __getitem__(self, index):
            image = Image.fromarray(decode_rgb(ROOT / config["dataset_dir"] / self.rows[index]["path"]))
            if self.training:
                image = augment(image)
            return transform(image), self.labels[index]

    train = Images("train", True)
    val = Images("validation")
    train_loader = DataLoader(train, batch_size=RECIPE["batch_size"], shuffle=True,
                              generator=torch.Generator().manual_seed(SEED), num_workers=0)
    val_loader = DataLoader(val, batch_size=RECIPE["batch_size"], shuffle=False, num_workers=0)
    counts = Counter(train.labels)
    weights = torch.tensor([len(train) / (len(classes) * counts[i]) for i in range(len(classes))],
                           dtype=torch.float32, device=device)
    loss_function = torch.nn.CrossEntropyLoss(weight=weights)

    def evaluate(loader):
        model.eval()
        logits, truth = [], []
        with torch.inference_mode():
            for images, labels in loader:
                logits.append(model(images.to(device)).cpu().numpy())
                truth.append(labels.numpy())
        scores, y = np.concatenate(logits), np.concatenate(truth)
        return scores, y, classification_metrics(y, scores, classes)

    checkpoint = CACHE / "finetune" / f"{model_id}.safetensors"
    checkpoint.parent.mkdir(parents=True, exist_ok=True)

    def save_best():
        save_file({k: v.detach().cpu().contiguous() for k, v in model.state_dict().items()}, str(checkpoint))

    _, _, best_metrics = evaluate(val_loader)
    best_key = validation_key(best_metrics)
    best_epoch, best_stage = 0, "initial_probe"
    save_best()
    history = [{"epoch": 0, "stage": best_stage, "validation": {k: best_metrics[k]
                for k in ("macro_f1", "top1_accuracy", "top3_accuracy")}}]
    started = time.perf_counter()
    epoch = 0
    trainable_finetune = None
    for stage, epochs in (("head_warmup", RECIPE["warmup_epochs"]), ("finetune", RECIPE["finetune_epochs"])):
        trainable = configure_trainable(model, model_id, stage == "finetune")
        if stage == "finetune":
            trainable_finetune = trainable
        groups = [{"params": model.head.parameters(), "lr": RECIPE["head_warmup_lr"] if stage == "head_warmup" else RECIPE["head_finetune_lr"]}]
        if stage == "finetune":
            groups.append({"params": [p for p in model.encoder.parameters() if p.requires_grad],
                           "lr": RECIPE["backbone_lr"]})
        optimizer = torch.optim.AdamW(groups, weight_decay=RECIPE["weight_decay"])
        stale = 0
        for _ in range(epochs):
            epoch += 1
            tick = time.perf_counter()
            model.train()
            if stage == "head_warmup":
                model.encoder.eval()
            for module in model.modules():
                if isinstance(module, torch.nn.modules.batchnorm._BatchNorm):
                    module.eval()
            running_loss = 0.0
            for images, labels in train_loader:
                optimizer.zero_grad(set_to_none=True)
                logits = model(images.to(device))
                loss = loss_function(logits, labels.to(device))
                if not torch.isfinite(loss):
                    raise ValueError("Non-finite training loss")
                loss.backward()
                optimizer.step()
                running_loss += float(loss.detach()) * len(labels)
            _, _, metrics = evaluate(val_loader)
            improved = validation_key(metrics) > best_key
            if improved:
                best_key = validation_key(metrics)
                best_metrics, best_epoch, best_stage = metrics, epoch, stage
                save_best()
                stale = 0
            else:
                stale += 1
            record = {"epoch": epoch, "stage": stage, "training_loss": running_loss / len(train),
                      "seconds": time.perf_counter() - tick, "selected": improved,
                      "validation": {k: metrics[k] for k in ("macro_f1", "top1_accuracy", "top3_accuracy")}}
            history.append(record)
            print(json.dumps({"model_id": model_id, **record}), flush=True)
            write_json(folder / "history.json", history)
            if stage == "finetune" and stale >= RECIPE["patience"]:
                break
        del optimizer
    training_seconds = time.perf_counter() - started
    model.load_state_dict(load_file(str(checkpoint)), strict=True)
    # TinyViT caches attention biases outside state_dict. Restoring weights
    # must also invalidate biases from the last evaluated training epoch.
    for module in model.modules():
        if hasattr(module, "attention_bias_cache"):
            module.attention_bias_cache.clear()
    model.eval().requires_grad_(False)
    val_logits, val_y, _ = evaluate(val_loader)
    temperature, calibration = fit_temperature(val_y, val_logits)
    threshold, threshold_info = select_threshold(val_y, softmax(val_logits / temperature, axis=1))
    val_metrics, _ = evaluated_metrics(val_y, val_logits, classes, temperature, threshold)
    val_metrics.update(split="validation", calibration=calibration, threshold_selection=threshold_info)
    tensor = val[0][0].unsqueeze(0).to(device)
    durations = []
    if device.type == "cuda":
        torch.cuda.reset_peak_memory_stats()
    with torch.inference_mode():
        for i in range(35):
            if device.type == "cuda":
                torch.cuda.synchronize()
            tick = time.perf_counter()
            model(tensor)
            if device.type == "cuda":
                torch.cuda.synchronize()
            if i >= 5:
                durations.append((time.perf_counter() - tick) * 1000)
    configuration = {
        **spec, "model_id": model_id + "_finetune", "base_model_id": model_id,
        "recipe": RECIPE, "method": "fine-tuning from linear probe; selected stage: " + best_stage,
        "selected_epoch": best_epoch, "selected_stage": best_stage, "epochs_executed": epoch,
        "training_seconds": training_seconds, "class_order": classes,
        "manifest_sha256": manifest_hash, "split_counts": EXPECTED_SPLITS,
        "pretrained_sha256": digest(pretrained), "initial_probe_sha256": digest(probe),
        "checkpoint_sha256": digest(checkpoint), "checkpoint_bytes": checkpoint.stat().st_size,
        "checkpoint_local": str(checkpoint.relative_to(ROOT)), "feature": feature,
        "preprocessing": preprocessing, "input_shape": list(tensor.shape),
        "parameters": sum(p.numel() for p in model.parameters()),
        "trainable_parameters_finetune": trainable_finetune,
        "temperature": temperature, "threshold": threshold,
        "test_used_for_selection": False, "mobile_export_status_file": "mobile_export.json (when present)",
        "timing": {"desktop_single_image_ms_median": statistics.median(durations),
                   "desktop_single_image_ms_p95": float(np.percentile(durations, 95)),
                   "peak_cuda_allocated_bytes_batch1": torch.cuda.max_memory_allocated() if device.type == "cuda" else None,
                   "note": "FP32, batch 1 encoder+linear head, 5 warmups/30 synchronized measurements. Excludes image preprocessing and calibration. Not Android latency."},
    }
    write_json(folder / "experiment_config.json", configuration)
    write_json(folder / "validation_metrics.json", val_metrics)
    # Only after saving the selected checkpoint, configuration and calibration, infer test images.
    test = Images("test")
    test_logits, test_y, _ = evaluate(DataLoader(test, batch_size=8, shuffle=False, num_workers=0))
    test_metrics, probabilities = evaluated_metrics(test_y, test_logits, classes, temperature, threshold)
    test_metrics.update(split="test", test_used_for_selection=False)
    write_json(folder / "test_metrics.json", test_metrics)
    np.savez_compressed(folder / "logits.npz", validation=val_logits, test=test_logits)
    save_confusion(folder / "confusion_matrix.csv", test_metrics["confusion_matrix"], classes)
    save_confusion(folder / "confusion_matrix_validation.csv", val_metrics["confusion_matrix"], classes)
    save_class_metrics(folder / "per_class_metrics.csv", {"validation": val_metrics, "test": test_metrics}, classes)
    save_predictions(folder / "predictions_test.csv", test.rows, test_y, test_logits,
                     probabilities, threshold, classes)
    for path in folder.glob("*.csv"):
        path.write_bytes(path.read_bytes().replace(b"\r\n", b"\n"))
    summary = {"status": "completed", "model_id": model_id + "_finetune",
               "name": spec["name"] + " fine-tuning", "selected_stage": best_stage,
               "selected_epoch": best_epoch,
               "validation": {k: val_metrics[k] for k in ("top1_accuracy", "macro_f1", "top3_accuracy")},
               "test": {k: test_metrics[k] for k in ("top1_accuracy", "macro_f1", "top3_accuracy", "coverage", "accepted_accuracy")},
               "test_decoded_after_freezing_selection_temperature_threshold": True}
    write_json(folder / "run_summary.json", summary)
    print(json.dumps(summary), flush=True)
    del model, encoder, train_loader, val_loader
    gc.collect()
    if device.type == "cuda":
        torch.cuda.empty_cache()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--models", nargs="+", choices=PUBLIC_MODELS, default=PUBLIC_MODELS)
    args = parser.parse_args()
    config = load_config(ROOT / "config.yaml")
    manifest = load_manifest(config, verify=True)
    classes = validate_manifest(manifest)
    if classes != json.loads((ROOT / "artifacts/classes.json").read_text()):
        raise ValueError("Classes differ from Android bundle")
    OUTPUT.mkdir(parents=True, exist_ok=True)
    torch.set_num_threads(2)
    torch.backends.cudnn.deterministic = True
    torch.backends.cudnn.benchmark = False
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    write_json(OUTPUT / "environment.json", {
        "python": platform.python_version(), "platform": platform.platform(),
        "device": str(device), "gpu": torch.cuda.get_device_name(0) if device.type == "cuda" else None,
        "cuda_runtime": torch.version.cuda, "torch": torch.__version__,
        "installed_packages": sorted(f"{p.metadata['Name']}=={p.version}" for p in distributions()),
    })
    write_json(OUTPUT / "protocol.json", {"models": args.models, "recipe": RECIPE,
                                        "export_selection": "top two fine-tuning runs by validation Macro-F1, Top-1, Top-3; test not used",
                                        "android_full_analysis_target_ms": 3000})
    for model_id in args.models:
        run(model_id, config, manifest, classes, device)


if __name__ == "__main__":
    main()
