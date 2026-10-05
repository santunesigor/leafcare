"""Isolated frozen-backbone comparison; never exports or modifies Android assets."""
import argparse
import gc
import json
import platform
import statistics
import time
import warnings
from importlib.metadata import distributions
from pathlib import Path

import numpy as np
from scipy.special import softmax
from sklearn.exceptions import ConvergenceWarning
from sklearn.linear_model import LogisticRegression

from experiments.dinov2.benchmark import (
    ROOT, SEED, EXPECTED_SPLITS, calibration_metrics, classification_metrics,
    digest, encode_labels, fit_temperature, manifest_digest, save_class_metrics,
    save_confusion, save_predictions, select_threshold, split_rows, validate_manifest,
)
from leafcare.common import load_config, write_json
from leafcare.dataset import load_manifest
from leafcare.preprocessing import decode_rgb

CACHE = ROOT / "experiments/vision/.cache"
OUTPUT = ROOT / "benchmark_artifacts/vision"
BATCH_SIZE = 8
MODELS = {
    "dinov3_vits16": {
        "name": "DINOv3 ViT-S/16", "loader": "transformers",
        "repo": "facebook/dinov3-vits16-pretrain-lvd1689m",
        "revision": "114c1379950215c8b35dfcd4e90a5c251dde0d32",
        "license": "DINOv3 License", "pretraining": "LVD-1689M",
    },
    "mobilenetv4_medium": {
        "name": "MobileNetV4 Conv Medium", "loader": "timm",
        "architecture": "mobilenetv4_conv_medium.e500_r224_in1k",
        "repo": "timm/mobilenetv4_conv_medium.e500_r224_in1k",
        "revision": "02a09fbfb82b289e871ba8255f9da58c056fb13b",
        "license": "Apache-2.0", "pretraining": "ImageNet-1k (timm)",
    },
    "tinyvit_5m": {
        "name": "TinyViT-5M", "loader": "timm",
        "architecture": "tiny_vit_5m_224.dist_in22k_ft_in1k",
        "repo": "timm/tiny_vit_5m_224.dist_in22k_ft_in1k",
        "revision": "b46989c2a2c95e7612b919a6f5e2cd7dcf4f1271",
        "license": "Apache-2.0 (timm checkpoint)", "pretraining": "ImageNet-22k distilled; fine-tuned ImageNet-1k",
    },
    "tinyvit_11m": {
        "name": "TinyViT-11M", "loader": "timm",
        "architecture": "tiny_vit_11m_224.dist_in22k_ft_in1k",
        "repo": "timm/tiny_vit_11m_224.dist_in22k_ft_in1k",
        "revision": "47df2afc2abd1405d5917a6ceabf9b921ca3d5ad",
        "license": "Apache-2.0 (timm checkpoint)", "pretraining": "ImageNet-22k distilled; fine-tuned ImageNet-1k",
    },
    "mobileclip2_s0": {
        "name": "MobileCLIP2-S0 image encoder", "loader": "open_clip",
        "repo": "apple/MobileCLIP2-S0",
        "revision": "3136ea51c8ed56b9f9abfab04cb816735aaad6cb",
        "license": "Apple ML Research Model TOU", "pretraining": "DFNDR-2B",
    },
    "dinov2_vitb14": {
        "name": "DINOv2 ViT-B/14", "loader": "timm_tokens",
        "architecture": "vit_base_patch14_dinov2.lvd142m",
        "repo": "timm/vit_base_patch14_dinov2.lvd142m",
        "revision": "4685c99dabffe5affac90bd99dbffd25801ae58d",
        "license": "Apache-2.0", "pretraining": "LVD-142M",
    },
}


def load_encoder(spec, local_dinov3=None):
    import torch
    from huggingface_hub import hf_hub_download

    def download(filename):
        return Path(hf_hub_download(spec["repo"], filename, revision=spec["revision"],
                                   cache_dir=str(CACHE / "weights")))

    if spec["loader"] in {"timm", "timm_tokens"}:
        import timm
        from safetensors.torch import load_file
        checkpoint = download("model.safetensors")
        model = timm.create_model(spec["architecture"], pretrained=False)
        model.load_state_dict(load_file(str(checkpoint)), strict=True)
        if spec["loader"] == "timm_tokens":
            model.set_input_size(img_size=224)
        model.reset_classifier(0)
        data_config = timm.data.resolve_model_data_config(model)
        if spec["loader"] == "timm_tokens":
            # Match the historical DINOv2 preprocessing, rather than pretraining at 518px.
            data_config.update(input_size=(3, 224, 224), interpolation="bicubic", crop_pct=0.875)
            class DinoTokens(torch.nn.Module):
                def __init__(self, backbone):
                    super().__init__()
                    self.backbone = backbone

                def forward(self, images):
                    tokens = self.backbone.forward_features(images)
                    return torch.cat((tokens[:, 0], tokens[:, self.backbone.num_prefix_tokens:].mean(dim=1)), dim=1)
            model = DinoTokens(model)
        transform = timm.data.create_transform(**data_config, is_training=False)
        feature = "CLS + mean patch tokens" if spec["loader"] == "timm_tokens" else "pooled pre-logits features"
        return model, transform, data_config, checkpoint, feature
    if spec["loader"] == "open_clip":
        import open_clip
        checkpoint = download("mobileclip2_s0.pt")
        model, _, transform = open_clip.create_model_and_transforms(
            "MobileCLIP2-S0", pretrained=str(checkpoint),
            image_mean=(0, 0, 0), image_std=(1, 1, 1))
        from timm.utils import reparameterize_model
        original = model.visual.eval()
        visual = reparameterize_model(original)
        # Reparameterization is an inference optimization, not a new learned candidate.
        with torch.inference_mode():
            sample = torch.rand(1, 3, 256, 256)
            torch.testing.assert_close(visual(sample), original(sample), atol=1e-4, rtol=1e-4)
        # Use only the image encoder. No text prompts or text encoder at inference.
        return visual, transform, {"transform": str(transform), "mean": [0, 0, 0],
                                   "std": [1, 1, 1], "reparameterization_parity_checked": True}, checkpoint, "image embedding"

    from transformers import AutoImageProcessor, AutoModel
    if local_dinov3:
        folder = Path(local_dinov3).resolve()
        checkpoint = folder / "model.safetensors"
    else:
        checkpoint = download("model.safetensors")
        for filename in ("config.json", "preprocessor_config.json"):
            download(filename)
        folder = checkpoint.parent
    encoder = AutoModel.from_pretrained(str(folder), local_files_only=True)
    processor = AutoImageProcessor.from_pretrained(str(folder), local_files_only=True)

    class DinoFeatures(torch.nn.Module):
        def __init__(self, backbone):
            super().__init__()
            self.backbone = backbone

        def forward(self, images):
            tokens = self.backbone(pixel_values=images).last_hidden_state
            start = 1 + self.backbone.config.num_register_tokens
            return torch.cat((tokens[:, 0], tokens[:, start:].mean(dim=1)), dim=1)

    def transform(image):
        return processor(images=image, return_tensors="pt")["pixel_values"][0]

    return DinoFeatures(encoder), transform, processor.to_dict(), checkpoint, "CLS + mean patch tokens (excluding registers)"


def fit_probes(train_x, train_y, val_x, val_y, classes):
    """Same two predeclared C=1 probes as the historical DINOv2 experiment."""
    candidates, classifiers, logits = [], [], []
    for name, weight in (("unweighted", None), ("balanced", "balanced")):
        classifier = LogisticRegression(C=1.0, class_weight=weight, max_iter=1000,
                                        solver="lbfgs", random_state=SEED, tol=1e-4)
        started = time.perf_counter()
        # Do not silently publish a probe which failed to converge.
        with warnings.catch_warnings():
            warnings.simplefilter("error", ConvergenceWarning)
            classifier.fit(train_x, train_y)
        scores = classifier.decision_function(val_x)
        metrics = classification_metrics(val_y, scores, classes)
        candidates.append({
            "name": name, "class_weight": weight, "C": 1.0,
            "iterations": classifier.n_iter_.tolist(),
            "training_seconds": time.perf_counter() - started,
            "validation": {k: metrics[k] for k in ("top1_accuracy", "macro_f1", "top3_accuracy")},
        })
        classifiers.append(classifier)
        logits.append(scores)
    best = max(range(len(candidates)), key=lambda i: (
        candidates[i]["validation"]["macro_f1"], candidates[i]["validation"]["top1_accuracy"],
        candidates[i]["validation"]["top3_accuracy"], candidates[i]["name"] == "unweighted"))
    return classifiers[best], logits[best], candidates, candidates[best]["name"]


def evaluated_metrics(truth, logits, classes, temperature, threshold):
    metrics = classification_metrics(truth, logits, classes)
    calibrated = calibration_metrics(truth, logits, temperature)
    probabilities = calibrated.pop("probabilities")
    accepted = probabilities.max(axis=1) >= threshold
    correct = probabilities.argmax(axis=1) == truth
    metrics.update({
        **calibrated, "threshold": threshold, "coverage": float(accepted.mean()),
        "accepted": int(accepted.sum()), "accepted_correct": int((correct & accepted).sum()),
        "accepted_accuracy": float(correct[accepted].mean()) if accepted.any() else None,
    })
    return metrics, probabilities


def run_model(model_id, config, manifest, classes, device, local_dinov3=None):
    import torch
    from PIL import Image
    from safetensors.torch import save_file
    from torch.utils.data import DataLoader, Dataset

    spec = MODELS[model_id]
    output = OUTPUT / model_id
    output.mkdir(parents=True, exist_ok=True)
    # An interrupted rerun must not leave an old result marked as newly completed.
    write_json(output / "run_summary.json", {
        "status": "running", "model_id": model_id, "name": spec["name"],
    })
    started = time.perf_counter()
    np.random.seed(SEED)
    torch.manual_seed(SEED)
    model, transform, preprocessing, checkpoint, feature = load_encoder(spec, local_dinov3)
    model.to(device).eval()
    model.requires_grad_(False)
    parameters = sum(p.numel() for p in model.parameters())
    checkpoint_hash = digest(checkpoint)
    manifest_hash = manifest_digest(ROOT / config["prepared_dir"] / "manifest.json")
    # Measure an image-only artifact consistently, rather than counting unused heads/text encoders.
    image_weights = CACHE / f"{model_id}_image_encoder.safetensors"
    save_file({k: v.detach().cpu().contiguous() for k, v in model.state_dict().items()}, str(image_weights))

    class Images(Dataset):
        def __init__(self, rows):
            self.rows = rows

        def __len__(self):
            return len(self.rows)

        def __getitem__(self, index):
            return transform(Image.fromarray(decode_rgb(
                ROOT / config["dataset_dir"] / self.rows[index]["path"])))

    def extract(split):
        rows = split_rows(manifest, split)
        labels = encode_labels(rows, classes)
        # Fresh extraction, no implicit reuse across changes in model/preprocessing.
        batches = []
        loader = DataLoader(Images(rows), batch_size=BATCH_SIZE, shuffle=False, num_workers=0)
        with torch.inference_mode():
            for images in loader:
                batches.append(model(images.to(device)).float().cpu().numpy())
        features = np.concatenate(batches)
        if features.ndim != 2 or not np.isfinite(features).all():
            raise ValueError("Invalid encoder features")
        return features, labels

    extraction_start = time.perf_counter()
    train_x, train_y = extract("train")
    val_x, val_y = extract("validation")
    extraction_seconds = time.perf_counter() - extraction_start
    classifier, val_logits, candidates, selected = fit_probes(train_x, train_y, val_x, val_y, classes)
    temperature, calibration = fit_temperature(val_y, val_logits)
    threshold, selection = select_threshold(val_y, softmax(val_logits / temperature, axis=1))
    val_metrics, _ = evaluated_metrics(val_y, val_logits, classes, temperature, threshold)
    val_metrics.update({"split": "validation", "calibration": calibration, "threshold_selection": selection})

    sample = transform(Image.fromarray(decode_rgb(
        ROOT / config["dataset_dir"] / split_rows(manifest, "validation")[0]["path"])))
    tensor = sample.unsqueeze(0).to(device)
    if device.type == "cuda":
        torch.cuda.reset_peak_memory_stats()
    durations = []
    with torch.inference_mode():
        for index in range(35):
            if device.type == "cuda":
                torch.cuda.synchronize()
            tick = time.perf_counter()
            model(tensor)
            if device.type == "cuda":
                torch.cuda.synchronize()
            if index >= 5:
                durations.append((time.perf_counter() - tick) * 1000)
    peak_bytes = torch.cuda.max_memory_allocated() if device.type == "cuda" else None
    write_json(output / "probe_comparison.json", candidates)
    write_json(output / "validation_metrics.json", val_metrics)
    np.savez_compressed(output / "classifier.npz", classes=np.asarray(classes),
                        coef=classifier.coef_, intercept=classifier.intercept_)
    experiment = {
        **spec, "model_id": model_id, "seed": SEED, "split_counts": EXPECTED_SPLITS,
        "manifest_sha256": manifest_hash, "class_order": classes,
        "checkpoint_sha256": checkpoint_hash, "checkpoint_bytes": checkpoint.stat().st_size,
        "image_encoder_bytes": image_weights.stat().st_size,
        "classifier_bytes": (output / "classifier.npz").stat().st_size,
        "backbone_parameters": parameters,
        "trainable_parameters": int(classifier.coef_.size + classifier.intercept_.size),
        "feature_dimension": int(train_x.shape[1]), "feature": feature,
        "preprocessing": preprocessing, "input_shape": list(tensor.shape), "dtype": "float32",
        "method": "frozen backbone + logistic regression; no augmentation or feature standardization",
        "classifier_selected_on_validation": selected, "classifier_C": 1.0,
        "optimizer": "LBFGS", "max_iter": 1000, "batch_size": BATCH_SIZE,
        "selection_metric": "validation Macro-F1; ties Top-1, Top-3, unweighted",
        "temperature": temperature, "threshold": threshold,
        "test_used_for_selection": False,
        "timing": {
            "feature_extraction_seconds_train_validation": extraction_seconds,
            "classifier_training_seconds": sum(c["training_seconds"] for c in candidates),
            "desktop_single_image_ms_median": statistics.median(durations),
            "desktop_single_image_ms_p95": float(np.percentile(durations, 95)),
            "peak_cuda_allocated_bytes_batch1": peak_bytes,
            "note": "Batch 1 encoder only, 5 warmups/30 samples, CUDA synchronized. Excludes decode, preprocessing, transfers and classifier; memory includes resident model. Not Android latency or total process RAM.",
        },
        "android_inference": "not measured", "mobile_export": "not attempted",
    }
    # Persist selection/calibration before the first test-image decoding/forward.
    write_json(output / "experiment_config.json", experiment)
    test_x, test_y = extract("test")
    test_logits = classifier.decision_function(test_x)
    test_metrics, probabilities = evaluated_metrics(test_y, test_logits, classes, temperature, threshold)
    test_metrics.update({"split": "test", "test_used_for_selection": False})
    write_json(output / "test_metrics.json", test_metrics)
    save_confusion(output / "confusion_matrix.csv", test_metrics["confusion_matrix"], classes)
    save_confusion(output / "confusion_matrix_validation.csv", val_metrics["confusion_matrix"], classes)
    save_class_metrics(output / "per_class_metrics.csv", {"validation": val_metrics, "test": test_metrics}, classes)
    save_predictions(output / "predictions_test.csv", split_rows(manifest, "test"), test_y,
                     test_logits, probabilities, threshold, classes)
    np.savez_compressed(output / "logits.npz", validation=val_logits, test=test_logits)
    result = {
        "status": "completed", "model_id": model_id, "name": spec["name"],
        "selected_probe": selected,
        "validation": {k: val_metrics[k] for k in ("top1_accuracy", "macro_f1", "top3_accuracy")},
        "test": {k: test_metrics[k] for k in ("top1_accuracy", "macro_f1", "top3_accuracy", "coverage", "accepted_accuracy")},
        "total_seconds": time.perf_counter() - started,
        "test_decoded_after_freezing_selection_temperature_threshold": True,
    }
    write_json(output / "run_summary.json", result)
    print(json.dumps(result, ensure_ascii=False), flush=True)
    del model, classifier, train_x, val_x, test_x
    gc.collect()
    if device.type == "cuda":
        torch.cuda.empty_cache()
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--models", nargs="+", choices=list(MODELS), default=list(MODELS))
    parser.add_argument("--validate-only", action="store_true")
    parser.add_argument("--dinov3-directory", help="Local HF snapshot containing model.safetensors and processor/config JSON")
    args = parser.parse_args()
    config = load_config(ROOT / "config.yaml")
    manifest = load_manifest(config, verify=True)
    classes = validate_manifest(manifest)
    if classes != json.loads((ROOT / "artifacts/classes.json").read_text()):
        raise ValueError("Class order differs from the integrated bundle")
    if args.validate_only:
        print("Verified 696 images by SHA256; fixed 489/104/103 split and class/group contract.")
        return
    import torch
    from huggingface_hub.errors import GatedRepoError
    CACHE.mkdir(parents=True, exist_ok=True)
    OUTPUT.mkdir(parents=True, exist_ok=True)
    torch.set_num_threads(2)
    torch.backends.cudnn.deterministic = True
    torch.backends.cudnn.benchmark = False
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    versions = {name: __import__(name).__version__ for name in
                ("torch", "torchvision", "timm", "open_clip", "transformers", "numpy", "sklearn", "scipy", "PIL", "huggingface_hub", "safetensors")}
    write_json(OUTPUT / "environment.json", {
        "python": platform.python_version(), "platform": platform.platform(), "versions": versions,
        "device": str(device), "cuda_runtime": torch.version.cuda,
        "gpu": torch.cuda.get_device_name(0) if device.type == "cuda" else None,
        "seed": SEED, "manifest_sha256": manifest_digest(ROOT / config["prepared_dir"] / "manifest.json"),
        "installed_packages": sorted(f"{p.metadata['Name']}=={p.version}" for p in distributions()),
    })
    for model_id in args.models:
        print(f"Starting {model_id}", flush=True)
        try:
            run_model(model_id, config, manifest, classes, device, args.dinov3_directory)
        except GatedRepoError:
            # Never include HTTP headers, authentication values or full exception text in artifacts.
            result = {"status": "access_restricted", "model_id": model_id, "name": MODELS[model_id]["name"],
                      "reason": "Official weights require account approval; no accessible checkpoint available. No training or metrics produced."}
            write_json(OUTPUT / model_id / "run_summary.json", result)
            print(json.dumps(result), flush=True)


if __name__ == "__main__":
    main()
