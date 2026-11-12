"""TFLite conversion and app-preprocessing replay of a cycle's candidate (export environment).

Conversion: the graph takes RGB 0-255 NHWC [1,224,224,3] and embeds ImageNet
normalization, the validation temperature and softmax, like the model in the app.
Every validation image must give the same Top-1 as PyTorch and as training, with a
maximum probability error of 1e-4.

Replay: every validation, TLA test and field test image is decoded the way the app
does (EXIF, sRGB, bicubic resize to 256, center crop 224) and run through the
candidate TFLite, which must reproduce the training probabilities and metrics. The
production model (artifacts/leafcare.tflite) is replayed on the same held-out
images, and comparison.json records the automatic recommendation.
"""
from __future__ import annotations

import time
from pathlib import Path

import numpy as np
import torch
from safetensors.torch import load_file
from scipy.special import softmax

from leafcare.common import ROOT, load_config, read_json, sha256, write_json
from leafcare.inference import TFLitePredictor
from leafcare.preprocessing import decode_rgb, preprocess, resize_distilled_rgb
from training.dataset import load_dataset, split_rows
from training.metrics import classification_metrics, encode_labels, thresholded
from training.promote import SUMMARY, recommendation
from training.train import Classifier, Images, clear_attention_cache, load_student

PRODUCTION = ROOT / "artifacts"
SAMPLE = ROOT.parent / "samples/reference_frog_eye.jpg"
MAX_ERROR = 1e-4


class MobileInput(torch.nn.Module):
    def __init__(self, classifier, mean, std, temperature):
        super().__init__()
        self.classifier = classifier
        self.register_buffer("mean", torch.tensor(mean).reshape(1, 3, 1, 1))
        self.register_buffer("std", torch.tensor(std).reshape(1, 3, 1, 1))
        self.temperature = temperature

    def forward(self, rgb):
        images = rgb.permute(0, 3, 1, 2) / 255.0
        return torch.softmax(self.classifier((images - self.mean) / self.std) / self.temperature, dim=1)


def check_parity(reference, converted, recorded):
    result = {
        "top1_agreement": float((reference.argmax(1) == converted.argmax(1)).mean()),
        "max_abs_error": float(np.abs(reference - converted).max()),
        "recorded_training_top1_agreement": float((reference.argmax(1) == recorded.argmax(1)).mean()),
        "recorded_training_max_abs_error": float(np.abs(reference - recorded).max()),
        "max_abs_error_limit": MAX_ERROR,
    }
    result["passed"] = (result["top1_agreement"] == result["recorded_training_top1_agreement"] == 1
                        and result["max_abs_error"] <= MAX_ERROR and result["recorded_training_max_abs_error"] <= MAX_ERROR)
    return result


def convert(run: Path) -> dict:
    """runs/<cycle>/model_float32.tflite and mobile_export.json; skipped when already done for this checkpoint."""
    import litert_torch
    from timm.layers import set_fused_attn

    config = read_json(run / "experiment_config.json")
    if read_json(run / "run_summary.json").get("status") != "completed":
        raise ValueError("O treino deste ciclo não foi concluído.")
    if sha256(run / config["checkpoint"]) != config["checkpoint_sha256"]:
        raise ValueError("O checkpoint mudou depois do treino.")
    destination = run / "model_float32.tflite"
    if (run / "mobile_export.json").is_file():
        previous = read_json(run / "mobile_export.json")
        if (previous.get("status") == "completed" and previous["source_checkpoint_sha256"] == config["checkpoint_sha256"]
                and destination.is_file() and sha256(destination) == previous["sha256"]):
            return previous
    torch.set_num_threads(2)
    set_fused_attn(False)
    encoder, transform, preprocessing = load_student(pretrained=False)
    classes = config["class_order"]
    state = load_file(str(run / config["checkpoint"]))
    classifier = Classifier(encoder, np.zeros(tuple(state["head.weight"].shape)), np.zeros(len(classes)))
    classifier.load_state_dict(state, strict=True)
    classifier.eval().requires_grad_(False)
    clear_attention_cache(classifier)
    dataset, roots = load_dataset(run / "dataset.json")
    val = Images(split_rows(dataset, "validation"), roots, classes, transform)
    mobile = MobileInput(classifier, preprocessing["mean"], preprocessing["std"], config["temperature"]).eval()
    tick = time.perf_counter()
    litert_torch.convert(mobile, (torch.zeros(1, 224, 224, 3),)).export(str(destination))
    seconds = time.perf_counter() - tick
    predictor = TFLitePredictor(destination, classes)
    mean = torch.tensor(preprocessing["mean"]).reshape(3, 1, 1)
    std = torch.tensor(preprocessing["std"]).reshape(3, 1, 1)
    references, predictions = [], []
    with torch.inference_mode():
        for index in range(len(val)):
            rgb = ((val[index][0] * std + mean) * 255).permute(1, 2, 0).unsqueeze(0).contiguous().numpy()
            references.append(mobile(torch.from_numpy(rgb)).numpy()[0])
            predictions.append(predictor.scores(rgb)[0])
    with np.load(run / "logits.npz", allow_pickle=False) as logits:
        recorded = softmax(logits["validation"] / config["temperature"], axis=1)
    parity = {**check_parity(np.array(references), np.array(predictions), recorded), "n_images": len(val), "split": "validation"}
    metadata = {
        "status": "completed" if parity["passed"] else "failed", "format": "TFLite float32",
        "path": destination.name, "sha256": sha256(destination), "bytes": destination.stat().st_size,
        "input_shape": [1, 224, 224, 3], "output_shape": [1, len(classes)], "class_order": classes,
        "temperature": config["temperature"], "threshold": config["threshold"],
        "source_checkpoint_sha256": config["checkpoint_sha256"], "conversion_seconds": seconds, "parity": parity,
        "operators": sorted({op["op_name"] for op in predictor.interpreter._get_ops_details()}),
        "litert_torch": litert_torch.__version__,
    }
    write_json(run / "mobile_export.json", metadata)
    if not parity["passed"]:
        raise ValueError("Paridade TFLite reprovada; veja mobile_export.json.")
    return metadata


def replay(run: Path) -> dict:
    """Replay candidate and production with the app preprocessing; writes replay.json and comparison.json."""
    config = read_json(run / "experiment_config.json")
    export = read_json(run / "mobile_export.json")
    if export.get("status") != "completed" or sha256(run / export["path"]) != export["sha256"]:
        raise ValueError("Exportação TFLite ausente, reprovada ou alterada.")
    dataset, roots = load_dataset(run / "dataset.json")
    classes = dataset["classes"]
    production_meta = read_json(PRODUCTION / "model_metadata.json")
    if sha256(PRODUCTION / "leafcare.tflite") != production_meta["model_sha256"]:
        raise ValueError("artifacts/leafcare.tflite diverge de model_metadata.json.")
    candidate, production = TFLitePredictor(run / export["path"], classes), TFLitePredictor(PRODUCTION / "leafcare.tflite", classes)
    results = {"candidate": {}, "production": {}}
    with np.load(run / "logits.npz", allow_pickle=False) as recorded:
        for split in ("validation", "test", "field_test"):
            if split not in recorded.files:
                continue
            rows = split_rows(dataset, split)
            truth = encode_labels(rows, classes)
            inputs = [resize_distilled_rgb(decode_rgb(roots[r["source"]] / r["path"]))[None, ...] for r in rows]
            probabilities = np.array([candidate.scores(x)[0] for x in inputs])
            expected = softmax(recorded[split] / config["temperature"], axis=1)
            error = float(np.max(np.abs(probabilities - expected)))
            if error > MAX_ERROR or not np.array_equal(probabilities.argmax(1), expected.argmax(1)):
                raise ValueError(f"Replay com o pré-processamento do app divergiu em {split}: erro {error:.2e}.")
            metrics = thresholded(classification_metrics(truth, probabilities, classes), truth, probabilities,
                                  np.float32(config["threshold"]))
            trained = read_json(run / f"{split}_metrics.json")
            for key in SUMMARY:
                if trained[key] is not None and abs(metrics[key] - trained[key]) > 1e-9:
                    raise ValueError(f"{split}: {key} do replay ({metrics[key]}) difere do treino ({trained[key]}).")
            results["candidate"][split] = {**metrics, "split": split, "model_sha256": export["sha256"],
                                           "app_preprocessing_max_abs_error": error, "test_used_for_selection": False}
            if split != "validation":
                scores = np.array([production.scores(x)[0] for x in inputs])
                results["production"][split] = {
                    **thresholded(classification_metrics(truth, scores, classes), truth, scores,
                                  np.float32(production_meta["confidence_threshold"])),
                    "split": split, "model_sha256": production_meta["model_sha256"]}
    write_json(run / "replay.json", results)
    write_json(run / "reference_prediction.json", {
        "image": SAMPLE.relative_to(ROOT.parent).as_posix(), "image_sha256": sha256(SAMPLE),
        "model_sha256": export["sha256"], "probabilities": candidate.scores(preprocess(SAMPLE))[0].tolist()})
    summary = read_json(run / "run_summary.json")
    comparison = recommendation(results["candidate"], results["production"], load_config()["max_tla_macro_f1_drop"],
                                smoke=summary.get("smoke_test", False))
    write_json(run / "comparison.json", {"production_model_sha256": production_meta["model_sha256"], **comparison})
    return comparison
