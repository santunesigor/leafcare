import json
from pathlib import Path

import numpy as np
import pytest

from leafcare.ensemble import build_ensemble, calibrated_mean


def test_average_then_temperature_scaling():
    parts = np.array([[[.8, .1, .1]], [[.6, .3, .1]], [[.4, .4, .2]]], dtype=np.float32)
    mean = parts.mean(axis=0)
    np.testing.assert_allclose(calibrated_mean(parts, 1), mean, atol=1e-7)
    powered = np.sqrt(mean)
    np.testing.assert_allclose(calibrated_mean(parts, 2), powered / powered.sum(axis=1, keepdims=True), atol=1e-7)


@pytest.mark.parametrize("temperature", [0, -1, float("nan"), float("inf")])
def test_invalid_temperature_is_rejected(temperature):
    with pytest.raises(ValueError):
        calibrated_mean(np.full((3, 1, 3), 1 / 3), temperature)


def test_incomplete_ensemble_and_invalid_probabilities_are_rejected():
    with pytest.raises(ValueError):
        calibrated_mean(np.full((2, 1, 3), 1 / 3), 1)
    with pytest.raises(ValueError):
        calibrated_mean(np.full((3, 1, 3), .1), 1)


@pytest.mark.parametrize("module_name,entry", [("legacy.evaluate", "evaluate"), ("export_tflite", "export")])
def test_legacy_pipeline_cannot_replace_ensemble_calibration(tmp_path, monkeypatch, module_name, entry):
    import importlib
    module = importlib.import_module(module_name)
    (tmp_path / "training_metadata.json").write_text('{"architecture": "MobileNetV3Ensemble"}')
    monkeypatch.setattr(module, "load_manifest", lambda config: {})
    with pytest.raises(ValueError, match="train_ensemble.py --export-only"):
        getattr(module, entry)({"_base": tmp_path, "output_dir": "."})


@pytest.mark.tensorflow
def test_ensemble_keras_reload_and_tflite_match_calibrated_mean(tmp_path):
    tf = pytest.importorskip("tensorflow")
    from export_tflite import convert_model
    from leafcare.inference import TFLitePredictor
    tf.keras.utils.set_random_seed(42)
    models = []
    for i, probabilities in enumerate(([.8, .1, .1], [.6, .3, .1], [.4, .4, .2])):
        inputs = tf.keras.Input((224, 224, 3))
        pooled = tf.keras.layers.GlobalAveragePooling2D()(inputs)
        scores = tf.keras.layers.Dense(3, activation="softmax", kernel_initializer="zeros",
            bias_initializer=tf.keras.initializers.Constant(np.log(probabilities)))(pooled)
        models.append(tf.keras.Model(inputs, scores, name=f"fixture_member_{i}"))
    model = build_ensemble(models, 1.37)
    tensor = np.full((1, 224, 224, 3), 127, np.float32)
    expected = calibrated_mean([m(tensor, training=False).numpy() for m in models], 1.37)[0]
    model.save(tmp_path / "ensemble.keras")
    restored = tf.keras.models.load_model(tmp_path / "ensemble.keras", compile=False)
    np.testing.assert_allclose(restored(tensor, training=False).numpy()[0], expected, atol=1e-7)
    candidate = tmp_path / "ensemble.tflite"
    candidate.write_bytes(convert_model(restored, tmp_path))
    converted, _ = TFLitePredictor(candidate, ["a", "b", "c"]).scores(tensor)
    np.testing.assert_allclose(converted, expected, atol=1e-6)


@pytest.mark.tensorflow
def test_default_app_bundle_executes_calibrated_ensemble():
    pytest.importorskip("tensorflow")
    from leafcare.common import sha256
    from leafcare.inference import TFLitePredictor, rank
    from leafcare.preprocessing import preprocess
    root = Path(__file__).resolve().parents[2]
    assets = root / "android-app/app/src/main/assets"
    metadata = json.loads((assets / "model_metadata.json").read_text())
    if metadata["architecture"] != "MobileNetV3Ensemble":
        pytest.skip("New ensemble has not been installed yet")
    assert metadata["member_count"] == len(metadata["members"]) == 3
    assert metadata["aggregation"] == "mean_probabilities"
    assert metadata["probability_calibration"] == "embedded_temperature_scaling"
    assert metadata["threshold_calibrated"] is True
    fixture = json.loads((root / "machine-learning/benchmark_artifacts/ensemble/reference_prediction.json").read_text())
    image = root / fixture["image"]
    assert sha256(image) == fixture["image_sha256"]
    scores, _ = TFLitePredictor(assets / "leafcare.tflite", metadata["classes"]).scores(preprocess(image))
    np.testing.assert_allclose(scores, fixture["probabilities"], atol=1e-4, rtol=0)
    result = rank(scores, metadata["classes"], metadata["confidence_threshold"])
    assert result["threshold"] == metadata["confidence_threshold"]
    assert sha256(assets / "leafcare.tflite") == metadata["model_sha256"]
    assert sha256(root / "machine-learning/artifacts/leafcare.tflite") == metadata["model_sha256"]


def test_ensemble_recorded_metrics_replay_from_deployed_probabilities():
    from experiments.dinov2.benchmark import classification_metrics, encode_labels, split_rows
    root = Path(__file__).resolve().parents[1]
    folder = root / "benchmark_artifacts/ensemble"
    summary = folder / "run_summary.json"
    if not summary.exists():
        pytest.skip("Ensemble export not completed")
    manifest = json.loads((root / "data/prepared/manifest.json").read_text())
    config = json.loads((folder / "experiment_config.json").read_text())
    assert config["composition_fixed_before_training"] is True
    assert config["test_used_for_selection"] is False
    with np.load(folder / "probabilities.npz", allow_pickle=False) as scores:
        for split in ("validation", "test"):
            truth = encode_labels(split_rows(manifest, split), manifest["classes"])
            metrics = classification_metrics(truth, scores[split], manifest["classes"])
            recorded = json.loads((folder / f"{split}_metrics.json").read_text())
            for key in ("top1_accuracy", "macro_f1", "top3_accuracy"):
                assert metrics[key] == recorded[key]
            accepted = scores[split].max(axis=1) >= np.float32(config["threshold"])
            assert int(accepted.sum()) == recorded["accepted"]
            assert float(accepted.mean()) == recorded["coverage"]
