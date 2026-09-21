from collections import Counter
import numpy as np
from .common import location
from .preprocessing import preprocess


def seed_everything(seed):
    import tensorflow as tf
    tf.keras.utils.set_random_seed(seed)
    tf.config.experimental.enable_op_determinism()
    tf.config.threading.set_inter_op_parallelism_threads(2)
    tf.config.threading.set_intra_op_parallelism_threads(2)


def make_dataset(config, manifest, split, augment=False):
    import tensorflow as tf
    rows = [r for r in manifest["rows"] if r["split"] == split]
    indices = {c: i for i, c in enumerate(manifest["classes"])}

    def generator():
        for row in rows:
            yield preprocess(location(config, "dataset_dir") / row["path"])[0], np.int32(indices[row["class_id"]])

    dataset = tf.data.Dataset.from_generator(generator, output_signature=(
        tf.TensorSpec((224, 224, 3), tf.float32), tf.TensorSpec((), tf.int32)))
    dataset = dataset.apply(tf.data.experimental.assert_cardinality(len(rows)))
    if augment:
        dataset = dataset.shuffle(len(rows), seed=config["seed"], reshuffle_each_iteration=True)
    dataset = dataset.batch(config["batch_size"])
    if augment:
        transforms = tf.keras.Sequential([
            tf.keras.layers.RandomFlip("horizontal_and_vertical", seed=config["seed"]),
            tf.keras.layers.RandomRotation(0.08, fill_mode="reflect", seed=config["seed"] + 1),
            tf.keras.layers.RandomZoom(0.10, fill_mode="reflect", seed=config["seed"] + 2),
            tf.keras.layers.RandomContrast(0.10, seed=config["seed"] + 3),
        ])
        dataset = dataset.map(lambda x, y: (tf.clip_by_value(transforms(x, training=True), 0., 255.), y), num_parallel_calls=1)
    options = tf.data.Options()
    options.experimental_deterministic = True
    options.threading.private_threadpool_size = 2
    return dataset.with_options(options).prefetch(1)


def build_model(num_classes, weights="imagenet"):
    import tensorflow as tf
    backbone = tf.keras.applications.MobileNetV3Small(
        input_shape=(224, 224, 3), include_top=False, include_preprocessing=True,
        weights=weights, pooling="avg")
    backbone.trainable = False
    inputs = tf.keras.Input((224, 224, 3), name="rgb_0_255")
    features = backbone(inputs, training=False)
    features = tf.keras.layers.Dropout(0.25)(features)
    outputs = tf.keras.layers.Dense(num_classes, activation="softmax", name="probabilities")(features)
    return tf.keras.Model(inputs, outputs, name="leafcare"), backbone


def class_weights(manifest, ratio_threshold):
    counts = Counter(r["class_id"] for r in manifest["rows"] if r["split"] == "train")
    if max(counts.values()) / min(counts.values()) < ratio_threshold:
        return None
    total, n = sum(counts.values()), len(counts)
    return {i: total / (n * counts[c]) for i, c in enumerate(manifest["classes"])}


def enable_finetuning(backbone, last_layers):
    import tensorflow as tf
    if last_layers < 1 or last_layers > len(backbone.layers):
        raise ValueError("fine_tune_last_layers fora do intervalo do backbone.")
    backbone.trainable = True
    start = len(backbone.layers) - last_layers
    for index, layer in enumerate(backbone.layers):
        layer.trainable = index >= start and not isinstance(layer, tf.keras.layers.BatchNormalization)


def compile_model(model, learning_rate):
    import tensorflow as tf
    model.compile(optimizer=tf.keras.optimizers.Adam(learning_rate), loss="sparse_categorical_crossentropy",
                  metrics=[tf.keras.metrics.SparseCategoricalAccuracy(name="accuracy")])


def callbacks(config, checkpoint):
    import tensorflow as tf
    return [
        tf.keras.callbacks.ModelCheckpoint(str(checkpoint), monitor="val_loss", save_best_only=True, save_weights_only=True),
        tf.keras.callbacks.EarlyStopping(monitor="val_loss", patience=config["early_stopping_patience"], restore_best_weights=True),
        tf.keras.callbacks.ReduceLROnPlateau(monitor="val_loss", factor=0.5, patience=3, min_lr=1e-7),
        tf.keras.callbacks.TerminateOnNaN(),
    ]
