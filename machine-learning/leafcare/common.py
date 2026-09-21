import hashlib
import json
from pathlib import Path

import yaml


def load_config(path="config.yaml"):
    path = Path(path).resolve()
    config = yaml.safe_load(path.read_text(encoding="utf-8"))
    config["_base"] = path.parent
    if config["image_size"] != 224:
        raise ValueError("Esta versão usa o contrato de entrada 224 × 224.")
    if not 0 <= config["confidence_threshold"] <= 1:
        raise ValueError("confidence_threshold deve estar entre 0 e 1.")
    ratios = config["split"]
    if set(ratios) != {"train", "validation", "test"} or any(v <= 0 for v in ratios.values()):
        raise ValueError("Defina três proporções positivas: train, validation e test.")
    if abs(sum(ratios.values()) - 1) > 1e-8:
        raise ValueError("As proporções da divisão devem somar 1.")
    return config


def location(config, key):
    return (config["_base"] / config[key]).resolve()


def write_json(path, value):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2, allow_nan=False) + "\n", encoding="utf-8")


def read_json(path):
    return json.loads(Path(path).read_text(encoding="utf-8"))


def sha256(path):
    h = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def classes_hash(classes):
    return hashlib.sha256("\n".join(classes).encode("utf-8")).hexdigest()


def contract(classes, threshold):
    return {
        "schema_version": 1, "status": "trained", "architecture": "MobileNetV3Small",
        "input_shape": [1, 224, 224, 3], "input_dtype": "float32", "output_dtype": "float32",
        "color_space": "RGB", "pixel_range": [0, 255],
        "normalization": "embedded_mobilenetv3_rescaling",
        "resize": "center_crop_bilinear_integer_v1", "max_image_pixels": 16000000,
        "classes": classes, "classes_sha256": classes_hash(classes),
        "confidence_threshold": threshold, "threshold_calibrated": False,
    }
