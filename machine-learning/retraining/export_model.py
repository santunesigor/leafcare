"""Convert a cycle's candidate to the app contract (TFLite float32) and check parity.

Runs in the export environment (experiments/distillation/requirements-export.txt).
The graph takes RGB 0-255 NHWC [1,224,224,3] and embeds ImageNet normalization,
the validation temperature and softmax, exactly like the model in the app. Every
validation image must give the same Top-1 as PyTorch and as training, with a
maximum probability error of 1e-4. Output: runs/<cycle>/model_float32.tflite and
mobile_export.json. Nothing is installed in the app.
"""
from __future__ import annotations

import argparse
import json
import time
from datetime import datetime, timezone
from pathlib import Path

import numpy as np
import torch
from safetensors.torch import load_file
from scipy.special import softmax
from timm.layers import set_fused_attn

from experiments.dinov2.benchmark import digest
from experiments.distillation import benchmark as recipe
from experiments.distillation.export import check_parity
from experiments.vision.export_candidates import MobileInput
from experiments.vision.finetune import Classifier
from leafcare.common import read_json, write_json
from retraining.train import RunImages, load_dataset

ROOT = Path(__file__).resolve().parents[1]


def load_candidate(run: Path):
    config = read_json(run / "experiment_config.json")
    if read_json(run / "run_summary.json").get("status") != "completed":
        raise ValueError("O treino deste ciclo não foi concluído.")
    if digest(run / config["checkpoint"]) != config["checkpoint_sha256"]:
        raise ValueError("O checkpoint mudou depois do treino.")
    encoder, transform, preprocessing, _ = recipe.load_student(config["base_model_id"])
    with np.load(run / "initial_probe.npz", allow_pickle=False) as probe:
        classifier = Classifier(encoder, probe["coef"], probe["intercept"])
    classifier.load_state_dict(load_file(str(run / config["checkpoint"])), strict=True)
    classifier.eval().requires_grad_(False)
    recipe.clear_attention_cache(classifier)
    return config, classifier, transform, preprocessing


def export(run: Path) -> dict:
    import litert_torch
    from ai_edge_litert.interpreter import Interpreter

    config, classifier, transform, preprocessing = load_candidate(run)
    dataset, roots = load_dataset(run / "dataset.json", datetime.now(timezone.utc))
    classes = dataset["classes"]
    val = RunImages([r for r in dataset["rows"] if r["split"] == "validation"], roots, classes, transform)
    mobile = MobileInput(classifier, preprocessing["mean"], preprocessing["std"], config["temperature"]).eval()
    destination = run / "model_float32.tflite"
    tick = time.perf_counter()
    litert_torch.convert(mobile, (torch.zeros(1, 224, 224, 3),)).export(str(destination))
    seconds = time.perf_counter() - tick
    interpreter = Interpreter(model_path=str(destination), num_threads=2)
    interpreter.allocate_tensors()
    inp, out = interpreter.get_input_details()[0], interpreter.get_output_details()[0]
    if list(inp["shape"]) != [1, 224, 224, 3] or inp["dtype"] != np.float32:
        raise ValueError("Entrada do TFLite fora do contrato do app.")
    if list(out["shape"]) != [1, len(classes)] or out["dtype"] != np.float32:
        raise ValueError("Saída do TFLite fora do contrato do app.")
    mean = torch.tensor(preprocessing["mean"]).reshape(3, 1, 1)
    std = torch.tensor(preprocessing["std"]).reshape(3, 1, 1)
    references, predictions = [], []
    with torch.inference_mode():
        for index in range(len(val)):
            rgb = ((val[index][0] * std + mean) * 255).permute(1, 2, 0).unsqueeze(0).contiguous().numpy()
            references.append(mobile(torch.from_numpy(rgb)).numpy()[0])
            interpreter.set_tensor(inp["index"], rgb)
            interpreter.invoke()
            predictions.append(interpreter.get_tensor(out["index"])[0])
    with np.load(run / "logits.npz", allow_pickle=False) as logits:
        recorded = softmax(logits["validation"] / config["temperature"], axis=1)
    parity = check_parity(np.array(references), np.array(predictions), recorded)
    parity.update(n_images=len(val), split="validation")
    metadata = {
        "status": "completed" if parity["passed"] else "failed", "format": "TFLite float32",
        "path": destination.name, "sha256": digest(destination), "bytes": destination.stat().st_size,
        "input_shape": [1, 224, 224, 3], "output_shape": [1, len(classes)], "class_order": classes,
        "temperature": config["temperature"], "threshold": config["threshold"],
        "source_checkpoint_sha256": config["checkpoint_sha256"], "conversion_seconds": seconds, "parity": parity,
        "operators": sorted({op["op_name"] for op in interpreter._get_ops_details()}),
        "litert_torch": litert_torch.__version__,
    }
    write_json(run / "mobile_export.json", metadata)
    if not parity["passed"]:
        raise ValueError("Paridade TFLite reprovada; veja mobile_export.json.")
    return metadata


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--run", required=True)
    args = parser.parse_args()
    torch.set_num_threads(2)
    set_fused_attn(False)
    metadata = export(ROOT / "runs" / args.run)
    print(json.dumps({k: metadata[k] for k in ("status", "bytes", "sha256", "parity")}, indent=2))


if __name__ == "__main__":
    main()
