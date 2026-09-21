import argparse
from datetime import datetime, timezone
from leafcare.common import load_config, location, write_json, sha256, contract
from leafcare.dataset import load_manifest


def train(config):
    manifest = load_manifest(config)
    output = location(config, "output_dir")
    if (output / "model.keras").exists():
        raise ValueError("Modelo já existente. Altere output_dir para preservar este experimento.")
    output.mkdir(parents=True, exist_ok=True)
    from leafcare.training import (seed_everything, make_dataset, build_model, class_weights,
                                  compile_model, callbacks, enable_finetuning)
    import tensorflow as tf
    import keras
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt

    seed_everything(config["seed"])
    train_ds = make_dataset(config, manifest, "train", augment=True)
    val_ds = make_dataset(config, manifest, "validation")
    # Sem fallback silencioso para pesos aleatórios se o download ImageNet falhar.
    model, backbone = build_model(len(manifest["classes"]), weights="imagenet")
    weights = class_weights(manifest, config["class_weight_ratio_threshold"])
    compile_model(model, config["learning_rate_frozen"])
    frozen_path = output / "frozen_best.weights.h5"
    history1 = model.fit(train_ds, validation_data=val_ds, epochs=config["epochs_frozen"],
                         class_weight=weights, callbacks=callbacks(config, frozen_path))
    model.load_weights(frozen_path)
    frozen_loss = float(model.evaluate(val_ds, verbose=0)[0])
    enable_finetuning(backbone, config["fine_tune_last_layers"])
    compile_model(model, config["learning_rate_finetune"])
    tuned_path = output / "finetune_best.weights.h5"
    history2 = model.fit(train_ds, validation_data=val_ds, epochs=config["epochs_finetune"],
                         class_weight=weights, callbacks=callbacks(config, tuned_path))
    model.load_weights(tuned_path)
    tuned_loss = float(model.evaluate(val_ds, verbose=0)[0])
    chosen = "finetune" if tuned_loss < frozen_loss else "frozen"
    model.load_weights(tuned_path if chosen == "finetune" else frozen_path)
    model.save(output / "model.keras")
    history = {k: [float(v) for v in history1.history[k] + history2.history[k]]
               for k in ("loss", "val_loss", "accuracy", "val_accuracy")}
    history["frozen_epochs"] = len(history1.history["loss"])
    write_json(output / "history.json", history)
    for metric, title in [("loss", "Perda"), ("accuracy", "Acurácia")]:
        fig, ax = plt.subplots(figsize=(8, 4.5))
        ax.plot(range(1, len(history[metric]) + 1), history[metric], label="Treino (com augmentation)")
        ax.plot(range(1, len(history[metric]) + 1), history["val_" + metric], label="Validação")
        ax.axvline(history["frozen_epochs"] + 0.5, color="gray", linestyle="--", label="Início do fine-tuning")
        ax.set(xlabel="Época", ylabel=title, title="LeafCare — " + title)
        ax.legend(); fig.tight_layout(); fig.savefig(output / (metric + ".png"), dpi=160); plt.close(fig)
    metadata = contract(manifest["classes"], config["confidence_threshold"])
    metadata.update(seed=config["seed"], tensorflow=tf.__version__, keras=keras.__version__,
                    trained_at=datetime.now(timezone.utc).isoformat(), selected_stage=chosen,
                    frozen_val_loss=frozen_loss, finetune_val_loss=tuned_loss,
                    keras_sha256=sha256(output / "model.keras"),
                    manifest_sha256=sha256(location(config, "prepared_dir") / "manifest.json"),
                    class_weights=weights, test_used_for_selection=False)
    write_json(output / "training_metadata.json", metadata)
    write_json(output / "classes.json", manifest["classes"])
    write_json(output / "config_used.json", {k: v for k, v in config.items() if not k.startswith("_")})
    print("Treinamento concluído. Execute evaluate.py e export_tflite.py.")


def main():
    parser = argparse.ArgumentParser(description="Transfer learning MobileNetV3Small em duas etapas.")
    parser.add_argument("--config", default="config.yaml")
    args = parser.parse_args()
    try:
        train(load_config(args.config))
    except (ValueError, FileNotFoundError) as error:
        parser.exit(2, f"Treinamento bloqueado: {error}\n")


if __name__ == "__main__":
    main()
