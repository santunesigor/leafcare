"""Inferência offline do ensemble selecionado no benchmark LeafCare.

O mesmo algoritmo está documentado para implementação em Kotlin/LiteRT:
pré-processar uma vez, executar três modelos, calcular a média das
probabilidades, aplicar temperature scaling e então obter top 3/limiar.
"""
import argparse
import json
import time
from pathlib import Path

import numpy as np

from leafcare.common import classes_hash, read_json, sha256
from leafcare.inference import TFLitePredictor, rank
from leafcare.preprocessing import preprocess


def calibrate(probabilities, temperature):
    probabilities = np.asarray(probabilities, dtype=np.float64)
    if temperature <= 0:
        raise ValueError("Temperatura de calibração deve ser positiva.")
    powered = np.power(np.clip(probabilities, 1e-12, 1.0), 1.0 / temperature)
    return (powered / powered.sum()).astype(np.float32)


def main():
    parser = argparse.ArgumentParser(description="Top 3 do ensemble LeafCare, totalmente offline.")
    parser.add_argument("image", type=Path)
    parser.add_argument(
        "--deployment",
        type=Path,
        default=Path("benchmark_artifacts/deployment"),
        help="Pasta que contém deployment_manifest.json e os TFLites float32.",
    )
    parser.add_argument("--threshold", type=float, help="Substitui o limiar validado do manifesto.")
    args = parser.parse_args()

    try:
        manifest = read_json(args.deployment / "deployment_manifest.json")
        classes = manifest["classes"]
        if classes_hash(classes) != manifest["classes_sha256"]:
            raise ValueError("Hash/ordem das classes incompatível.")
        if manifest["ensemble"] != "mean_probabilities":
            raise ValueError("Método de ensemble desconhecido.")

        tensor = preprocess(args.image)
        member_scores = []
        timings = []
        total_start = time.perf_counter()
        for member in manifest["members"]:
            path = args.deployment / member["float32"]
            if sha256(path) != member["float32_sha256"]:
                raise ValueError(f"Hash inválido para {path.name}.")
            predictor = TFLitePredictor(path, classes)
            scores, elapsed = predictor.scores(tensor)
            member_scores.append(scores)
            timings.append({"model": member["id"], "milliseconds": elapsed})

        averaged = np.mean(np.stack(member_scores), axis=0)
        calibrated = calibrate(averaged, float(manifest["temperature"]))
        threshold = args.threshold if args.threshold is not None else manifest["confidence_threshold"]
        result = rank(calibrated, classes, threshold)
        result.update(
            {
                "ensemble_members": [member["id"] for member in manifest["members"]],
                "temperature": manifest["temperature"],
                "member_inference_ms": timings,
                "total_ms": (time.perf_counter() - total_start) * 1000,
            }
        )
        print(json.dumps(result, ensure_ascii=False, indent=2))
    except (ValueError, FileNotFoundError, KeyError) as error:
        parser.exit(2, f"Inferência indisponível: {error}\n")


if __name__ == "__main__":
    main()
