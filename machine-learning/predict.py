import argparse
import json
from pathlib import Path

from leafcare.common import classes_hash, read_json, sha256
from leafcare.inference import TFLitePredictor


def main():
    parser = argparse.ArgumentParser(description="Retorna top 3 e confiança com o modelo do app, sem acesso à rede.")
    parser.add_argument("image", type=Path)
    parser.add_argument("--artifacts", type=Path, default=Path(__file__).resolve().parent / "artifacts")
    parser.add_argument("--threshold", type=float, help="Limiar de confiança; padrão: o calibrado do modelo.")
    args = parser.parse_args()
    try:
        meta = read_json(args.artifacts / "model_metadata.json")
        classes = read_json(args.artifacts / "classes.json")
        if meta["status"] != "trained" or meta["classes"] != classes or meta["classes_sha256"] != classes_hash(classes):
            raise ValueError("Modelo/classes ainda não treinados ou incompatíveis.")
        threshold = args.threshold if args.threshold is not None else meta["confidence_threshold"]
        model_path = args.artifacts / "leafcare.tflite"
        if sha256(model_path) != meta["model_sha256"]:
            raise ValueError("Hash do modelo não corresponde ao contrato.")
        result = TFLitePredictor(model_path, classes).predict(args.image, classes, threshold)
        print(json.dumps(result, ensure_ascii=False, indent=2))
    except (ValueError, FileNotFoundError) as error:
        parser.exit(2, f"Inferência indisponível: {error}\n")


if __name__ == "__main__":
    main()
