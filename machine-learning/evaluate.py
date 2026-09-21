import argparse
import numpy as np
from leafcare.common import load_config, location, read_json, write_json, sha256
from leafcare.dataset import load_manifest


def evaluate(config):
    manifest = load_manifest(config)
    output = location(config, "output_dir")
    metadata = read_json(output / "training_metadata.json")
    if metadata["manifest_sha256"] != sha256(location(config, "prepared_dir") / "manifest.json"):
        raise ValueError("Manifesto diferente do utilizado no treinamento.")
    if metadata["classes"] != manifest["classes"] or metadata["keras_sha256"] != sha256(output / "model.keras"):
        raise ValueError("Modelo/classes divergentes do treinamento.")
    import tensorflow as tf
    from sklearn.metrics import classification_report, confusion_matrix, accuracy_score, f1_score
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    from leafcare.training import seed_everything, make_dataset

    seed_everything(config["seed"])
    model = tf.keras.models.load_model(output / "model.keras", compile=False)
    classes = manifest["classes"]
    rows = [r for r in manifest["rows"] if r["split"] == "test"]
    ds = make_dataset(config, manifest, "test")
    probabilities = model.predict(ds, verbose=0)
    truth = np.array([classes.index(r["class_id"]) for r in rows])
    predictions = probabilities.argmax(axis=1)
    accepted = probabilities.max(axis=1) >= config["confidence_threshold"]
    metrics = {
        "status": "evaluated", "split": "test", "n_images": len(truth), "classes": classes,
        "accuracy": float(accuracy_score(truth, predictions)),
        "macro_f1": float(f1_score(truth, predictions, average="macro", zero_division=0)),
        "top3_accuracy": float(np.mean([y in np.argsort(-p, kind="stable")[:3] for y, p in zip(truth, probabilities)])),
        "threshold": config["confidence_threshold"], "threshold_calibrated": False,
        "coverage": float(accepted.mean()),
        "accepted_accuracy": float(accuracy_score(truth[accepted], predictions[accepted])) if accepted.any() else None,
        "classification_report": classification_report(truth, predictions, labels=list(range(len(classes))),
                              target_names=classes, output_dict=True, zero_division=0),
        "model_sha256": metadata["keras_sha256"], "manifest_sha256": metadata["manifest_sha256"],
    }
    write_json(output / "metrics.json", metrics)
    write_json(output / "test_predictions.json", [{"path": r["path"], "true_class": r["class_id"],
               "probabilities": p.tolist()} for r, p in zip(rows, probabilities)])
    matrix = confusion_matrix(truth, predictions, labels=list(range(len(classes))))
    write_json(output / "confusion_matrix.json", {"classes": classes, "matrix": matrix.tolist()})
    fig, ax = plt.subplots(figsize=(max(8, len(classes) * 0.65), max(6, len(classes) * 0.6)))
    plot = ax.imshow(matrix, cmap="Greens")
    ax.set(xticks=range(len(classes)), yticks=range(len(classes)), xticklabels=classes, yticklabels=classes,
           xlabel="Classe prevista", ylabel="Classe verdadeira", title="LeafCare — matriz de confusão (teste)")
    plt.setp(ax.get_xticklabels(), rotation=60, ha="right", fontsize=8)
    for (i, j), value in np.ndenumerate(matrix):
        ax.text(j, i, str(value), ha="center", va="center", fontsize=8,
                color="white" if value > matrix.max() / 2 else "black")
    fig.colorbar(plot, ax=ax); fig.tight_layout(); fig.savefig(output / "confusion_matrix.png", dpi=160); plt.close(fig)
    print(f"Teste: {len(truth)} imagens; acurácia={metrics['accuracy']:.4f}; macro-F1={metrics['macro_f1']:.4f}")


def main():
    parser = argparse.ArgumentParser(description="Avalia uma única versão congelada no conjunto de teste.")
    parser.add_argument("--config", default="config.yaml")
    args = parser.parse_args()
    try:
        evaluate(load_config(args.config))
    except (ValueError, FileNotFoundError) as error:
        parser.exit(2, f"Avaliação bloqueada: {error}\n")


if __name__ == "__main__":
    main()
