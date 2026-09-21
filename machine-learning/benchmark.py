"""Benchmark reproduzível de modelos móveis; seleção usa somente validação."""
import argparse
import gc
import json
import time
from collections import Counter
from pathlib import Path

import numpy as np
import tensorflow as tf

from leafcare.common import load_config, location, write_json
from leafcare.dataset import load_manifest
from leafcare.preprocessing import preprocess


# 14 configurações de candidatos a benchmarkar
CANDIDATES = [
    {"id": "m3s_base", "arch": "MobileNetV3Small", "drop": 0.25, "aug": "standard", "ft": 30, "lr": 1e-3},
    {"id": "m3s_strong", "arch": "MobileNetV3Small", "drop": 0.35, "aug": "strong", "ft": 45, "lr": 7e-4},
    {"id": "m3l_base", "arch": "MobileNetV3Large", "drop": 0.30, "aug": "standard", "ft": 40, "lr": 7e-4},
    {"id": "m3l_strong", "arch": "MobileNetV3Large", "drop": 0.40, "aug": "strong", "ft": 60, "lr": 5e-4},
    {"id": "effb0_base", "arch": "EfficientNetB0", "drop": 0.30, "aug": "standard", "ft": 40, "lr": 7e-4},
    {"id": "effb0_strong", "arch": "EfficientNetB0", "drop": 0.40, "aug": "strong", "ft": 60, "lr": 5e-4},
    {"id": "effv2b0_base", "arch": "EfficientNetV2B0", "drop": 0.30, "aug": "standard", "ft": 40, "lr": 7e-4},
    {"id": "effv2b0_strong", "arch": "EfficientNetV2B0", "drop": 0.40, "aug": "strong", "ft": 60, "lr": 5e-4},
    {"id": "m2_base", "arch": "MobileNetV2", "drop": 0.30, "aug": "standard", "ft": 40, "lr": 7e-4},
    {"id": "nasnet_base", "arch": "NASNetMobile", "drop": 0.35, "aug": "standard", "ft": 50, "lr": 5e-4},
    {"id": "m3s_adam", "arch": "MobileNetV3Small", "drop": 0.25, "aug": "standard", "ft": 30, "lr": 1e-3, "optimizer": "adam"},
    {"id": "m3s_no_weights", "arch": "MobileNetV3Small", "drop": 0.25, "aug": "standard", "ft": 30, "lr": 1e-3, "balance": "none"},
    {"id": "m3s_rmsprop", "arch": "MobileNetV3Small", "drop": 0.25, "aug": "standard", "ft": 30, "lr": 7e-4, "optimizer": "rmsprop"},
    {"id": "m3s_low_dropout", "arch": "MobileNetV3Small", "drop": 0.15, "aug": "standard", "ft": 45, "lr": 7e-4},
]


def make_dataset(config, manifest, split, aug="none"):
    """Cria tf.data.Dataset para o split especificado."""
    rows = [r for r in manifest["rows"] if r["split"] == split]
    class_to_idx = {x: i for i, x in enumerate(manifest["classes"])}
    size = config["image_size"]

    def generator():
        for r in rows:
            yield preprocess(location(config, "dataset_dir") / r["path"], size)[0], np.int32(class_to_idx[r["class_id"]])

    ds = tf.data.Dataset.from_generator(
        generator,
        output_signature=(
            tf.TensorSpec((size, size, 3), tf.float32),
            tf.TensorSpec((), tf.int32),
        ),
    ).apply(tf.data.experimental.assert_cardinality(len(rows)))

    if aug != "none":
        ds = ds.shuffle(len(rows), seed=config["seed"], reshuffle_each_iteration=True)

    ds = ds.batch(config["batch_size"])

    if aug != "none":
        layers = [
            tf.keras.layers.RandomFlip("horizontal_and_vertical", seed=43),
            tf.keras.layers.RandomRotation(0.08, fill_mode="reflect", seed=44),
            tf.keras.layers.RandomZoom(0.1, fill_mode="reflect", seed=45),
            tf.keras.layers.RandomContrast(0.12, seed=46),
        ]
        if aug == "strong":
            layers += [
                tf.keras.layers.RandomTranslation(0.08, 0.08, fill_mode="reflect", seed=47),
                tf.keras.layers.RandomBrightness(0.12, value_range=(0.0, 255.0), seed=48),
            ]
        augmentation = tf.keras.Sequential(layers)
        ds = ds.map(
            lambda x, y: (tf.clip_by_value(augmentation(x, training=True), 0.0, 255.0), y),
            num_parallel_calls=2,
        )

    return ds.prefetch(1)


def build_model(spec, num_classes, image_size):
    """Constrói modelo Keras com backbone pré-treinado e cabeça de classificação."""
    app = getattr(tf.keras.applications, spec["arch"])
    kw = dict(
        input_shape=(image_size, image_size, 3),
        include_top=False,
        weights="imagenet",
        pooling="avg",
    )
    # MobileNetV3 e EfficientNetV2 têm pré-processamento integrado
    if spec["arch"] in {"MobileNetV3Small", "MobileNetV3Large", "EfficientNetV2B0"}:
        kw["include_preprocessing"] = True

    base = app(**kw)
    base.trainable = False

    inp = tf.keras.Input((image_size, image_size, 3), name="rgb_0_255")
    x = inp

    # MobileNetV2 e NASNetMobile precisam de Rescaling explícito para manter contrato 0-255
    if spec["arch"] in {"MobileNetV2", "NASNetMobile"}:
        x = tf.keras.layers.Rescaling(1 / 127.5, offset=-1, name="embedded_rescaling")(x)

    x = base(x, training=False)
    x = tf.keras.layers.Dropout(spec["drop"])(x)
    out = tf.keras.layers.Dense(num_classes, activation="softmax", name="probabilities")(x)

    return tf.keras.Model(inp, out, name="leafcare_" + spec["id"]), base


def compile_model(model, lr, spec):
    """Configura otimizador conforme especificação do candidato."""
    if spec.get("optimizer") == "adam":
        optimizer = tf.keras.optimizers.Adam(lr)
    elif spec.get("optimizer") == "rmsprop":
        optimizer = tf.keras.optimizers.RMSprop(lr, momentum=0.9)
    else:
        optimizer = tf.keras.optimizers.AdamW(lr, weight_decay=1e-5)

    model.compile(optimizer, "sparse_categorical_crossentropy", metrics=["accuracy"])


def compute_metrics(y_true, y_pred):
    """Calcula métricas de validação: accuracy, macro-F1, top-3, confiança média."""
    from sklearn.metrics import f1_score

    y_pred_idx = y_pred.argmax(1)
    return {
        "accuracy": float(np.mean(y_pred_idx == y_true)),
        "macro_f1": float(f1_score(y_true, y_pred_idx, average="macro", zero_division=0)),
        "top3_accuracy": float(
            np.mean([v in np.argsort(-q)[:3] for v, q in zip(y_true, y_pred)])
        ),
        "mean_confidence": float(y_pred.max(1).mean()),
    }


def get_training_callbacks(checkpoint_path):
    """Callbacks padrão: checkpoint, early stopping, reduce LR, terminate on NaN."""
    return [
        tf.keras.callbacks.ModelCheckpoint(
            checkpoint_path, monitor="val_loss", save_best_only=True, save_weights_only=True
        ),
        tf.keras.callbacks.EarlyStopping(monitor="val_loss", patience=3, restore_best_weights=True),
        tf.keras.callbacks.ReduceLROnPlateau(monitor="val_loss", patience=2, factor=0.35, min_lr=1e-7),
        tf.keras.callbacks.TerminateOnNaN(),
    ]


def run(config, only=None):
    """Executa benchmark completo para todos os candidatos (ou --only)."""
    tf.config.threading.set_inter_op_parallelism_threads(2)
    tf.config.threading.set_intra_op_parallelism_threads(6)
    tf.config.experimental.enable_op_determinism()

    manifest = load_manifest(config)
    root = location(config, "benchmark_dir")
    root.mkdir(parents=True, exist_ok=True)

    # Dataset de validação e labels
    val_ds = make_dataset(config, manifest, "validation")
    val_rows = [r for r in manifest["rows"] if r["split"] == "validation"]
    y_val = np.array([manifest["classes"].index(r["class_id"]) for r in val_rows])

    # Pesos de classe (inversamente proporcionais à frequência de treino)
    train_counts = Counter(r["class_id"] for r in manifest["rows"] if r["split"] == "train")
    total = sum(train_counts.values())
    class_weights = {
        i: total / (len(train_counts) * train_counts[cls])
        for i, cls in enumerate(manifest["classes"])
    }

    selected = [s for s in CANDIDATES if only in (None, s["id"])]
    if not selected:
        raise ValueError("Candidato desconhecido")

    for spec in selected:
        out_dir = root / spec["id"]
        if (out_dir / "validation.json").exists():
            print(f"{spec['id']} já concluído")
            continue

        out_dir.mkdir(parents=True, exist_ok=True)
        tf.keras.backend.clear_session()
        tf.keras.utils.set_random_seed(config["seed"] + CANDIDATES.index(spec))

        model, base = build_model(spec, len(manifest["classes"]), config["image_size"])
        train_ds = make_dataset(config, manifest, "train", spec["aug"])
        used_weights = class_weights if spec.get("balance", "weights") == "weights" else None

        # Etapa 1: backbone congelado
        frozen_path = out_dir / "frozen.weights.h5"
        compile_model(model, spec["lr"], spec)
        started = time.time()
        history_frozen = model.fit(
            train_ds,
            validation_data=val_ds,
            epochs=config["benchmark"]["frozen_epochs"],
            class_weight=used_weights,
            callbacks=get_training_callbacks(frozen_path),
            verbose=2,
        )
        model.load_weights(frozen_path)
        loss_frozen = float(model.evaluate(val_ds, verbose=0)[0])

        # Etapa 2: fine-tuning das últimas N camadas (BatchNorm congelado)
        base.trainable = True
        start_layer = len(base.layers) - spec["ft"]
        for i, layer in enumerate(base.layers):
            layer.trainable = i >= start_layer and not isinstance(layer, tf.keras.layers.BatchNormalization)

        tuned_path = out_dir / "tuned.weights.h5"
        compile_model(model, config["benchmark"]["finetune_lr"], spec)
        history_finetune = model.fit(
            train_ds,
            validation_data=val_ds,
            epochs=config["benchmark"]["finetune_epochs"],
            class_weight=used_weights,
            callbacks=get_training_callbacks(tuned_path),
            verbose=2,
        )
        model.load_weights(tuned_path)
        loss_finetune = float(model.evaluate(val_ds, verbose=0)[0])

        # Escolhe melhor etapa pela val_loss
        stage = "finetune"
        if loss_frozen <= loss_finetune:
            model.load_weights(frozen_path)
            stage = "frozen"

        # Salva modelo e probabilidades de validação
        model.save(out_dir / "model.keras")
        val_probs = model.predict(val_ds, verbose=0)
        np.save(out_dir / "validation_probabilities.npy", val_probs)

        # Métricas e metadados
        result = {
            **spec,
            **compute_metrics(y_val, val_probs),
            "selected_stage": stage,
            "frozen_val_loss": loss_frozen,
            "finetune_val_loss": loss_finetune,
            "epochs_frozen": len(history_frozen.history["loss"]),
            "epochs_finetune": len(history_finetune.history["loss"]),
            "seconds": time.time() - started,
            "parameters": model.count_params(),
            "keras_bytes": (out_dir / "model.keras").stat().st_size,
        }

        write_json(out_dir / "history.json", {"frozen": history_frozen.history, "finetune": history_finetune.history})
        write_json(out_dir / "validation.json", result)
        print(json.dumps(result))
        gc.collect()

    # Ranking final por macro-F1, accuracy, top3
    all_results = [json.loads(p.read_text()) for p in root.glob("*/validation.json")]
    all_results.sort(key=lambda x: (x["macro_f1"], x["accuracy"], x["top3_accuracy"]), reverse=True)
    write_json(root / "ranking_validation.json", all_results)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--config", default="config.benchmark.yaml")
    parser.add_argument("--only")
    args = parser.parse_args()
    run(load_config(args.config), args.only)