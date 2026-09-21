import argparse
import json
from pathlib import Path
from leafcare.common import read_json, sha256, classes_hash
from leafcare.inference import TFLitePredictor, rank
from leafcare.preprocessing import preprocess


def main():
    parser = argparse.ArgumentParser(description="Retorna top 3 e confiança, sem acesso à rede.")
    parser.add_argument("image", type=Path)
    parser.add_argument("--artifacts", type=Path, default=Path("artifacts"))
    parser.add_argument("--backend", choices=["tflite", "keras"], default="tflite")
    parser.add_argument("--threshold", type=float)
    args = parser.parse_args()
    try:
        meta = read_json(args.artifacts / "model_metadata.json")
        classes = read_json(args.artifacts / "classes.json")
        if meta["status"] != "trained" or meta["classes"] != classes or meta["classes_sha256"] != classes_hash(classes):
            raise ValueError("Modelo/classes ainda não treinados ou incompatíveis.")
        threshold = args.threshold if args.threshold is not None else meta["confidence_threshold"]
        if not 0 <= threshold <= 1:
            raise ValueError("Limiar deve estar entre 0 e 1.")
        model_path = args.artifacts / ("leafcare.tflite" if args.backend == "tflite" else "model.keras")
        if sha256(model_path) != meta["model_sha256" if args.backend == "tflite" else "keras_sha256"]:
            raise ValueError("Hash do modelo não corresponde ao contrato.")
        if args.backend == "tflite":
            result = TFLitePredictor(model_path, classes).predict(args.image, classes, threshold)
        else:
            import tensorflow as tf
            import time
            model = tf.keras.models.load_model(model_path, compile=False)
            tensor = preprocess(args.image)
            start = time.perf_counter()
            scores = model(tensor, training=False).numpy()[0]
            elapsed = (time.perf_counter() - start) * 1000
            result = {**rank(scores, classes, threshold), "inference_ms": elapsed}
        print(json.dumps(result, ensure_ascii=False, indent=2))
    except (ValueError, FileNotFoundError) as error:
        parser.exit(2, f"Inferência indisponível: {error}\n")


if __name__ == "__main__":
    main()
