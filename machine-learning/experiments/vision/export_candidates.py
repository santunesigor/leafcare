"""Export the two validation-selected fine-tuning finalists to experimental TFLite."""
import argparse
import gc
import json
import platform
import statistics
import time
from importlib.metadata import distributions
from pathlib import Path

import numpy as np
import torch
from PIL import Image
from safetensors.torch import load_file

from experiments.vision.benchmark import CACHE, MODELS, ROOT, digest, load_encoder, split_rows
from experiments.vision.finetune import Classifier, OUTPUT
from leafcare.common import load_config, write_json
from leafcare.dataset import load_manifest
from leafcare.preprocessing import decode_rgb

ANDROID_REFERENCE = "Samsung Galaxy A06"

def select_finalists():
    summaries = []
    for path in OUTPUT.glob("*/run_summary.json"):
        summary = json.loads(path.read_text())
        if summary["status"] == "completed":
            summaries.append((path.parent.name, summary))
    protocol = json.loads((OUTPUT / "protocol.json").read_text())
    if {key for key, _ in summaries} != set(protocol["models"]):
        raise ValueError("All predeclared training runs must finish before selecting finalists")
    summaries.sort(key=lambda item: tuple(item[1]["validation"][k]
                   for k in ("macro_f1", "top1_accuracy", "top3_accuracy")), reverse=True)
    return [key for key, _ in summaries[:2]]


class MobileInput(torch.nn.Module):
    def __init__(self, classifier, mean, std, temperature):
        super().__init__()
        self.classifier = classifier
        self.register_buffer("mean", torch.tensor(mean).reshape(1, 3, 1, 1))
        self.register_buffer("std", torch.tensor(std).reshape(1, 3, 1, 1))
        self.temperature = temperature

    def forward(self, rgb):
        images = rgb.permute(0, 3, 1, 2) / 255.0
        logits = self.classifier((images - self.mean) / self.std)
        return torch.softmax(logits / self.temperature, dim=1)


def export(model_id, config, manifest):
    import litert_torch
    from ai_edge_litert.interpreter import Interpreter
    from timm.layers import set_fused_attn
    set_fused_attn(False)
    folder = OUTPUT / model_id
    configuration = json.loads((folder / "experiment_config.json").read_text())
    source = ROOT / configuration["checkpoint_local"]
    if digest(source) != configuration["checkpoint_sha256"]:
        raise ValueError("Selected checkpoint changed")
    encoder, transform, preprocessing, _, _ = load_encoder(MODELS[model_id], reparameterize=False)
    with np.load(ROOT / "benchmark_artifacts/vision" / model_id / "classifier.npz", allow_pickle=False) as initial:
        classifier = Classifier(encoder, initial["coef"], initial["intercept"])
    classifier.load_state_dict(load_file(str(source)), strict=True)
    classifier.eval().requires_grad_(False)
    # Fuse only at inference, after restoring the selected unfused training checkpoint.
    if model_id == "mobileclip2_s0":
        from timm.utils import reparameterize_model
        classifier.encoder = reparameterize_model(classifier.encoder)
    mean = preprocessing["mean"]
    std = preprocessing["std"]
    mobile = MobileInput(classifier, mean, std, configuration["temperature"]).eval()
    height, width = configuration["input_shape"][-2:]
    sample = torch.zeros(1, height, width, 3)
    destination = CACHE / "mobile" / f"{model_id}_float32.tflite"
    destination.parent.mkdir(parents=True, exist_ok=True)
    write_json(folder / "mobile_export.json", {"status": "running", "model_id": model_id})
    print(f"Converting {model_id} to TFLite float32", flush=True)
    tick = time.perf_counter()
    converted = litert_torch.convert(mobile, (sample,))
    converted.export(str(destination))
    conversion_seconds = time.perf_counter() - tick
    # TinyViT caches attention biases while tracing. Discard traced tensor
    # subclasses before computing the independent eager PyTorch reference.
    for module in mobile.modules():
        if hasattr(module, "attention_bias_cache"):
            module.attention_bias_cache.clear()
    interpreter = Interpreter(model_path=str(destination), num_threads=2)
    interpreter.allocate_tensors()
    input_info = interpreter.get_input_details()[0]
    output_info = interpreter.get_output_details()[0]
    if list(input_info["shape"]) != [1, height, width, 3] or input_info["dtype"] != np.float32:
        raise ValueError("Unexpected exported input contract")
    if list(output_info["shape"]) != [1, len(manifest["classes"])] or output_info["dtype"] != np.float32:
        raise ValueError("Unexpected exported output contract")
    rows = split_rows(manifest, "validation")
    references, predictions = [], []
    mean_tensor = torch.tensor(mean).reshape(3, 1, 1)
    std_tensor = torch.tensor(std).reshape(3, 1, 1)

    def input_for(row):
        normalized = transform(Image.fromarray(decode_rgb(ROOT / config["dataset_dir"] / row["path"])))
        rgb = ((normalized * std_tensor + mean_tensor) * 255).permute(1, 2, 0).unsqueeze(0)
        return rgb.contiguous().numpy()

    with torch.inference_mode():
        for row in rows:
            rgb = input_for(row)
            references.append(mobile(torch.from_numpy(rgb)).numpy()[0])
            interpreter.set_tensor(input_info["index"], rgb)
            interpreter.invoke()
            predictions.append(interpreter.get_tensor(output_info["index"])[0])
    reference = np.asarray(references)
    predicted = np.asarray(predictions)
    with np.load(folder / "logits.npz", allow_pickle=False) as stored:
        from scipy.special import softmax
        recorded = softmax(stored["validation"] / configuration["temperature"], axis=1)
    agreement = float((reference.argmax(axis=1) == predicted.argmax(axis=1)).mean())
    historical_agreement = float((reference.argmax(axis=1) == recorded.argmax(axis=1)).mean())
    max_error = float(np.abs(reference - predicted).max())
    maximum_recorded_error = float(np.abs(reference - recorded).max())
    parity = {"split": "validation", "n_images": len(rows), "top1_agreement": agreement,
              "max_abs_error": max_error, "recorded_training_top1_agreement": historical_agreement,
              "recorded_training_max_abs_error": maximum_recorded_error,
              "max_abs_error_limit": 1e-4}
    passed = agreement == 1 and historical_agreement == 1 and max_error <= 1e-4 and maximum_recorded_error <= 1e-4
    write_json(folder / "conversion_parity.json", {**parity, "passed": passed})
    np.savez_compressed(folder / "conversion_probabilities.npz", reference=reference, tflite=predicted)
    if not passed:
        raise ValueError("Conversion parity failed; experimental model must not be deployed")
    rgb = input_for(rows[0])
    inference, pipeline = [], []
    for i in range(35):
        interpreter.set_tensor(input_info["index"], rgb)
        start = time.perf_counter()
        interpreter.invoke()
        if i >= 5:
            inference.append((time.perf_counter() - start) * 1000)
        start = time.perf_counter()
        image = input_for(rows[i % len(rows)])
        interpreter.set_tensor(input_info["index"], image)
        interpreter.invoke()
        scores = interpreter.get_tensor(output_info["index"])[0]
        np.argsort(-scores, kind="stable")[:3]
        if i >= 5:
            pipeline.append((time.perf_counter() - start) * 1000)
    metadata = {
        "status": "completed", "experimental_only": True, "model_id": model_id,
        "selected_by": "top two fine-tuning runs ranked on validation Macro-F1, Top-1, Top-3",
        "format": "TFLite float32", "path_local": str(destination.relative_to(ROOT)),
        "bytes": destination.stat().st_size, "sha256": digest(destination),
        "input_shape": list(input_info["shape"].astype(int)), "input_dtype": "float32",
        "output_shape": [1, len(manifest["classes"])], "output_dtype": "float32",
        "class_order": manifest["classes"], "preprocessing": preprocessing,
        "normalization": "embedded RGB 0-255 to 0-1, model mean/std, temperature and softmax",
        "temperature": configuration["temperature"], "threshold": configuration["threshold"],
        "conversion_seconds": conversion_seconds, "parity": parity,
        "runtime": {"python": platform.python_version(), "device": "desktop CPU, Intel Core i5-13400",
                    "litert_torch": litert_torch.__version__, "torch": torch.__version__,
                    "installed_packages": sorted(f"{p.metadata['Name']}=={p.version}" for p in distributions())},
        "desktop_tflite_ms_median": statistics.median(inference),
        "desktop_tflite_ms_p95": float(np.percentile(inference, 95)),
        "desktop_python_pipeline_ms_median": statistics.median(pipeline),
        "desktop_python_pipeline_ms_p95": float(np.percentile(pipeline, 95)),
        "timing_note": "CPU two threads, 5 warmups/30 measurements. Python pipeline includes file decode, official preprocessing, TFLite and Top-3; excludes Android Room/UI. Not an Android measurement.",
        "android_full_analysis_ms": None, "android_target_ms": 3000,
        "android_reference_device": ANDROID_REFERENCE,
        "android_target_verified": False,
        "android_blocker": "No Android device connected; original app assets not replaced",
        "operators": sorted({op["op_name"] for op in interpreter._get_ops_details()}),
    }
    # numpy integer scalars must not leak into JSON.
    metadata["input_shape"] = [int(i) for i in input_info["shape"]]
    write_json(folder / "mobile_export.json", metadata)
    print(json.dumps({k: metadata[k] for k in ("model_id", "bytes", "desktop_tflite_ms_median", "desktop_python_pipeline_ms_median")}), flush=True)
    del model_id, mobile, classifier, encoder, converted, interpreter
    gc.collect()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--models", nargs="+", help="Retry a subset of the validation-selected finalists")
    args = parser.parse_args()
    torch.set_num_threads(2)
    config = load_config(ROOT / "config.yaml")
    manifest = load_manifest(config, verify=True)
    finalists = select_finalists()
    if args.models and not set(args.models) <= set(finalists):
        raise ValueError("Only validation-selected finalists can be exported")
    write_json(OUTPUT / "export_selection.json", {"models": finalists,
                                                "metric": "validation Macro-F1; ties Top-1/Top-3",
                                                "android_reference_device": ANDROID_REFERENCE,
                                                "test_used_for_selection": False})
    failed = []
    for model_id in args.models or finalists:
        try:
            export(model_id, config, manifest)
        except Exception as error:
            write_json(OUTPUT / model_id / "mobile_export.json", {
                "status": "failed", "experimental_only": True, "model_id": model_id,
                "error": str(error), "android_target_verified": False,
            })
            print(f"Export failed for {model_id}: {error}", flush=True)
            failed.append(model_id)
            gc.collect()
    if failed:
        raise RuntimeError("Exports failed: " + ", ".join(failed))


if __name__ == "__main__":
    main()
