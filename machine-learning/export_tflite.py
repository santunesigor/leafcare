import argparse
import shutil
import tempfile
from pathlib import Path
import numpy as np
from leafcare.common import load_config, location, read_json, write_json, sha256
from leafcare.dataset import load_manifest
from leafcare.preprocessing import preprocess
from leafcare.inference import TFLitePredictor, rank


def convert_model(model, directory):
    import tensorflow as tf
    saved = Path(directory) / "saved_model"
    model.export(str(saved), format="tf_saved_model")
    converter = tf.lite.TFLiteConverter.from_saved_model(str(saved))
    converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS]
    # Float32 de ponta a ponta: evita normalizações/quantizações divergentes.
    return converter.convert()


def export(config):
    manifest = load_manifest(config)
    output = location(config, "output_dir")
    meta = read_json(output / "training_metadata.json")
    if meta["classes"] != manifest["classes"] or meta["keras_sha256"] != sha256(output / "model.keras"):
        raise ValueError("Modelo/classes não correspondem ao treinamento.")
    if read_json(output / "classes.json") != manifest["classes"]:
        raise ValueError("classes.json foi alterado após o treinamento.")
    if meta["manifest_sha256"] != sha256(location(config, "prepared_dir") / "manifest.json"):
        raise ValueError("Manifesto alterado após treinamento.")
    import tensorflow as tf
    model = tf.keras.models.load_model(output / "model.keras", compile=False)
    classes = manifest["classes"]
    rows = [r for r in manifest["rows"] if r["split"] == "validation"]
    rng = np.random.default_rng(config["seed"])
    rows = [rows[i] for i in rng.permutation(len(rows))[:config["export"]["parity_images"]]]
    if not rows:
        raise ValueError("É necessária ao menos uma imagem de validação para comparar os modelos.")
    with tempfile.TemporaryDirectory() as temporary:
        candidate = Path(temporary) / "leafcare.tflite"
        candidate.write_bytes(convert_model(model, temporary))
        predictor = TFLitePredictor(candidate, classes)
        comparisons = []
        for row in rows:
            tensor = preprocess(location(config, "dataset_dir") / row["path"])
            original = model(tensor, training=False).numpy()[0]
            lite, elapsed = predictor.scores(tensor)
            rank(lite, classes, config["confidence_threshold"])
            comparisons.append({"path": row["path"], "max_abs_error": float(np.max(np.abs(original - lite))),
                                "top1_agrees": int(original.argmax()) == int(lite.argmax()), "inference_ms": elapsed})
        parity = {"split": "validation", "n_images": len(rows), "images": comparisons,
                  "max_abs_error": max(r["max_abs_error"] for r in comparisons),
                  "top1_agreement": sum(r["top1_agrees"] for r in comparisons) / len(rows)}
        write_json(output / "conversion_parity.json", parity)
        if parity["max_abs_error"] > config["export"]["parity_max_abs_error"] or parity["top1_agreement"] != 1:
            raise ValueError("Paridade reprovada. Assets não foram substituídos; revise conversion_parity.json.")
        shutil.copy2(candidate, output / "leafcare.tflite")
    meta.update(model_sha256=sha256(output / "leafcare.tflite"), confidence_threshold=config["confidence_threshold"],
                conversion_parity=parity)
    catalog = read_json(location(config, "catalog_file"))
    entries = {key: catalog["classes"].get(key, {
        "name": key, "scientific_name": "Não informado no catálogo local",
        "description": "Categoria do dataset. Conteúdo explicativo ainda não revisado.",
        "symptoms": ["Confira os sinais observados com um profissional."],
        "favorable_conditions": "Dependem da causa; necessitam de avaliação técnica.",
        "guidance": "Registre outras folhas e procure orientação profissional.", "reviewed": False,
    }) for key in classes}
    write_json(output / "model_metadata.json", meta)
    write_json(output / "diseases.json", {"classes": entries, "sources": catalog.get("sources", [])})
    target = (config["_base"] / config["export"]["android_assets_dir"]).resolve()
    target.mkdir(parents=True, exist_ok=True)
    for name in ("leafcare.tflite", "classes.json", "model_metadata.json", "diseases.json"):
        shutil.copy2(output / name, target / name)
    print("Modelo float32 exportado, paridade aprovada e quatro assets copiados para o Android.")


def main():
    parser = argparse.ArgumentParser(description="Exporta TFLite, compara com Keras e atualiza assets Android.")
    parser.add_argument("--config", default="config.yaml")
    args = parser.parse_args()
    try:
        export(load_config(args.config))
    except (ValueError, FileNotFoundError) as error:
        parser.exit(2, f"Exportação bloqueada: {error}\n")


if __name__ == "__main__":
    main()
