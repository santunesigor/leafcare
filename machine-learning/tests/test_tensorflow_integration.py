"""Rede temporária de teste: pesos aleatórios, SEM treinamento/validação de doenças.

Nenhum peso criado aqui é entregue no aplicativo ou reportado como modelo LeafCare.
"""
from pathlib import Path
import numpy as np
import pytest

@pytest.mark.tensorflow
def test_mobilenet_freeze_finetune_and_tflite_parity(tmp_path):
    tf = pytest.importorskip("tensorflow")
    from leafcare.training import build_model, enable_finetuning, compile_model
    from leafcare.preprocessing import preprocess
    from leafcare.inference import TFLitePredictor
    from export_tflite import convert_model
    tf.keras.utils.set_random_seed(42)
    model, backbone = build_model(3, weights=None)
    assert not backbone.trainable and len(model.trainable_weights) == 2
    image = Path(__file__).resolve().parents[2] / "samples/reference_frog_eye.jpg"
    tensor = preprocess(image)
    # Exercita os caminhos de gradiente: teste de implementação, sem métricas agronômicas.
    compile_model(model, .001)
    model.train_on_batch(tensor, np.array([0]))
    enable_finetuning(backbone, 30)
    assert len(backbone.trainable_weights) > 0
    assert all(not layer.trainable for layer in backbone.layers if isinstance(layer, tf.keras.layers.BatchNormalization))
    compile_model(model, .00001)
    model.train_on_batch(tensor, np.array([0]))
    model.save(tmp_path / "test.keras")
    model = tf.keras.models.load_model(tmp_path / "test.keras", compile=False)
    candidate = tmp_path / "test.tflite"
    candidate.write_bytes(convert_model(model, tmp_path))
    original = model(tensor, training=False).numpy()[0]
    converted, _ = TFLitePredictor(candidate, ["fixture_a", "fixture_b", "fixture_c"]).scores(tensor)
    np.testing.assert_allclose(original, converted, atol=1e-4, rtol=0)
    assert int(original.argmax()) == int(converted.argmax())
