import argparse
import json
import math
from pathlib import Path

from leafcare.common import classes_hash, read_json, sha256
from leafcare.preprocessing import DISTILLED_RESIZE


def validate(root):
    root = Path(root)
    assets = root / "android-app/app/src/main/assets"
    artifacts = root / "machine-learning/artifacts"
    meta = read_json(assets / "model_metadata.json")
    classes = read_json(assets / "classes.json")
    diseases = read_json(assets / "diseases.json")["classes"]
    if len(classes) < 3 or len(set(classes)) != len(classes):
        raise ValueError("Lista de classes inválida.")
    if set(classes) - set(diseases):
        raise ValueError("Catálogo local não cobre as classes.")
    if meta["classes"] != classes or classes_hash(classes) != meta["classes_sha256"]:
        raise ValueError("Classes divergentes nos metadados Android.")
    model = assets / "leafcare.tflite"
    if not model.is_file():
        raise FileNotFoundError("leafcare.tflite ausente nos assets Android.")
    if meta["status"] != "trained" or sha256(model) != meta["model_sha256"]:
        raise ValueError("Modelo divergente dos metadados.")
    if (meta.get("architecture") != "MobileNetV4SmallDistilled" or meta.get("schema_version") != 2
            or meta.get("resize") != DISTILLED_RESIZE
            or meta.get("normalization") != "embedded_imagenet_mean_std"
            or meta.get("probability_calibration") != "embedded_temperature_scaling"
            or meta.get("threshold_calibrated") is not True
            or not math.isfinite(meta.get("temperature", 0)) or meta.get("temperature", 0) <= 0
            or not 0 <= meta.get("confidence_threshold", -1) <= 1
            or meta.get("input_shape") != [1, 224, 224, 3] or meta.get("output_shape") != [1, len(classes)]
            or meta.get("input_dtype") != "float32" or meta.get("output_dtype") != "float32"):
        raise ValueError("Contrato do modelo destilado incompatível.")
    if sha256(artifacts / "leafcare.tflite") != meta["model_sha256"] or read_json(artifacts / "model_metadata.json") != meta:
        raise ValueError("Bundle Python e Android divergentes.")
    if read_json(artifacts / "classes.json") != classes:
        raise ValueError("Classes diferentes no artefato Python e Android.")
    from leafcare.inference import TFLitePredictor
    TFLitePredictor(model, classes)
    return {"status": "model_bundle_valid", "classes": len(classes), "trained_model_present": True,
            "model_output_order_verified": True, "model_sha256": meta["model_sha256"]}


def main():
    parser = argparse.ArgumentParser(description="Confere catálogo, contrato, hashes e dimensões do modelo Android.")
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parent.parent)
    parser.add_argument("--require-model", action="store_true",
                        help="Aceito por compatibilidade; o modelo é sempre exigido.")
    args = parser.parse_args()
    try:
        print(json.dumps(validate(args.root), ensure_ascii=False, indent=2))
    except (ValueError, FileNotFoundError) as error:
        parser.exit(2, f"Validação bloqueada: {error}\n")


if __name__ == "__main__":
    main()
