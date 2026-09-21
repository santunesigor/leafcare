import argparse
import json
from pathlib import Path
from leafcare.common import read_json, sha256, classes_hash


def validate(root, require_model=False):
    root = Path(root)
    assets = root / "android-app/app/src/main/assets"
    meta = read_json(assets / "model_metadata.json")
    classes = read_json(assets / "classes.json")
    diseases = read_json(assets / "diseases.json")["classes"]
    if len(classes) < 3 or len(set(classes)) != len(classes):
        raise ValueError("Lista de classes inválida.")
    if set(classes) - set(diseases):
        raise ValueError("Catálogo local não cobre as classes.")
    if meta["classes"] != classes:
        raise ValueError("Classes divergentes nos metadados Android.")
    model = assets / "leafcare.tflite"
    if not model.is_file():
        if require_model:
            raise FileNotFoundError("leafcare.tflite ausente: dataset completo e treinamento pendentes.")
        if meta["status"] == "trained":
            raise ValueError("Metadados afirmam treinamento sem modelo.")
        reference = read_json(root / "machine-learning/classes.reference.json")
        if reference != classes:
            raise ValueError("Classes de referência divergentes entre Python e Android.")
        return {"status": "source_bundle_valid", "reference_classes": len(classes), "trained_model_present": False,
                "model_output_order_verified": False, "message": "Código e catálogo consistentes. Modelo treinado pendente."}
    if meta["status"] != "trained" or sha256(model) != meta["model_sha256"]:
        raise ValueError("Modelo divergente dos metadados.")
    if classes_hash(classes) != meta["classes_sha256"]:
        raise ValueError("Hash das classes divergente.")
    from leafcare.inference import TFLitePredictor
    TFLitePredictor(model, classes)
    artifact_classes = root / "machine-learning/artifacts/classes.json"
    if artifact_classes.exists() and read_json(artifact_classes) != classes:
        raise ValueError("Classes diferentes no artefato Python e Android.")
    return {"status": "model_bundle_valid", "classes": len(classes), "trained_model_present": True,
            "model_output_order_verified": True, "model_sha256": meta["model_sha256"]}


def main():
    parser = argparse.ArgumentParser(description="Confere catálogo, contrato, hashes e dimensões do modelo Android.")
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parent.parent)
    parser.add_argument("--require-model", action="store_true")
    args = parser.parse_args()
    try:
        print(json.dumps(validate(args.root, args.require_model), ensure_ascii=False, indent=2))
    except (ValueError, FileNotFoundError) as error:
        parser.exit(2, f"Validação bloqueada: {error}\n")


if __name__ == "__main__":
    main()
