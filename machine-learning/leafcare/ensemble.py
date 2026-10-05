"""Mean-probability ensemble with calibration embedded in the exported graph."""
import numpy as np
from scipy.special import softmax


def calibrated_mean(probabilities, temperature):
    values = np.asarray(probabilities, dtype=np.float32)
    if values.ndim != 3 or len(values) != 3 or temperature <= 0 or not np.isfinite(temperature):
        raise ValueError("Expected three probability matrices and a positive temperature")
    if not np.isfinite(values).all() or (values < 0).any() or (values > 1).any():
        raise ValueError("Invalid member probabilities")
    if not np.allclose(values.sum(axis=-1), 1, atol=1e-4):
        raise ValueError("Member scores must sum to one")
    mean = values.mean(axis=0)
    return softmax(np.log(np.clip(mean, 1e-7, 1)) / temperature, axis=-1).astype(np.float32)


def build_ensemble(models, temperature):
    import keras
    if len(models) != 3 or temperature <= 0 or not np.isfinite(temperature):
        raise ValueError("Expected three models and a positive temperature")
    inputs = keras.Input((224, 224, 3), name="rgb_0_255")
    mean = keras.layers.Average(name="mean_probabilities")([model(inputs, training=False) for model in models])
    logits = keras.ops.log(keras.ops.clip(mean, 1e-7, 1.0)) / float(temperature)
    return keras.Model(inputs, keras.ops.softmax(logits, axis=-1), name="leafcare_ensemble")
