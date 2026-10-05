import csv
import json
import re

import numpy as np
import pytest

from experiments.vision.benchmark import (
    MODELS, OUTPUT, evaluated_metrics, fit_probes,
)


def test_checkpoint_revisions_are_pinned():
    assert all(re.fullmatch(r"[0-9a-f]{40}", spec["revision"]) for spec in MODELS.values())


def test_interrupted_rerun_invalidates_previous_completed_status(tmp_path, monkeypatch):
    import torch
    from experiments.vision import benchmark
    folder = tmp_path / "tinyvit_5m"
    folder.mkdir()
    (folder / "run_summary.json").write_text('{"status": "completed"}')
    monkeypatch.setattr(benchmark, "OUTPUT", tmp_path)

    def unavailable(*args, **kwargs):
        raise RuntimeError("unavailable checkpoint")

    monkeypatch.setattr(benchmark, "load_encoder", unavailable)
    with pytest.raises(RuntimeError, match="unavailable checkpoint"):
        benchmark.run_model("tinyvit_5m", {}, {}, [], torch.device("cpu"))
    assert json.loads((folder / "run_summary.json").read_text())["status"] == "running"


def test_probe_selection_uses_validation_macro_f1_and_keeps_class_order():
    classes = [f"c{i}" for i in range(16)]
    features = np.tile(np.eye(16), (3, 1))
    labels = np.tile(np.arange(16), 3)
    classifier, logits, candidates, selected = fit_probes(
        features, labels, np.eye(16), np.arange(16), classes)
    assert classifier.classes_.tolist() == list(range(16))
    assert logits.argmax(axis=1).tolist() == list(range(16))
    assert selected == "unweighted"  # The specified tie-break, identical balanced training.
    assert len(candidates) == 2
    assert candidates[0]["validation"]["macro_f1"] == 1.0


def test_rejection_metrics_handle_no_accepted_predictions():
    metrics, probabilities = evaluated_metrics(
        np.array([0, 1]), np.zeros((2, 16)), [f"c{i}" for i in range(16)], 1.0, 0.9)
    assert metrics["accepted"] == metrics["accepted_correct"] == 0
    assert metrics["coverage"] == 0
    assert metrics["accepted_accuracy"] is None
    np.testing.assert_allclose(probabilities.sum(axis=1), 1)


def test_comparison_preserves_historical_candidates_and_missing_measurements():
    from experiments.vision.report import HISTORICAL_NAMES, build_rows
    if not (OUTPUT / "comparison.csv").exists():
        pytest.skip("Comparison not generated")
    rows = build_rows()
    by_id = {row["id"]: row for row in rows}
    assert set(HISTORICAL_NAMES) <= set(by_id)
    scores = [row["validation_macro_f1"] for row in rows if "validation_macro_f1" in row]
    assert scores == sorted(scores, reverse=True)
    for model_id in HISTORICAL_NAMES:
        row = by_id[model_id]
        assert row["parameters"] > 0 and row["input_height"] == row["input_width"] == 224
        assert "test_top1" not in row
        assert "android_latency_ms" not in row


@pytest.mark.parametrize("model_id", list(MODELS))
def test_recorded_metrics_can_be_recomputed_from_logits(model_id):
    from experiments.dinov2.benchmark import ROOT, encode_labels, split_rows
    folder = OUTPUT / model_id
    summary_path = folder / "run_summary.json"
    if not summary_path.exists():
        pytest.skip("Experiment not executed")
    summary = json.loads(summary_path.read_text())
    if summary["status"] != "completed":
        assert summary["status"] == "access_restricted"
        assert not (folder / "test_metrics.json").exists()
        return
    manifest = json.loads((ROOT / "data/prepared/manifest.json").read_text())
    config = json.loads((folder / "experiment_config.json").read_text())
    assert config["class_order"] == manifest["classes"]
    assert config["test_used_for_selection"] is False
    assert summary["test_decoded_after_freezing_selection_temperature_threshold"] is True
    with np.load(folder / "logits.npz", allow_pickle=False) as stored:
        for split in ("validation", "test"):
            rows = split_rows(manifest, split)
            truth = encode_labels(rows, manifest["classes"])
            metrics, _ = evaluated_metrics(truth, stored[split], manifest["classes"],
                                            config["temperature"], config["threshold"])
            recorded = json.loads((folder / f"{split}_metrics.json").read_text())
            for key in ("top1_accuracy", "macro_f1", "top3_accuracy", "coverage", "accepted_accuracy"):
                assert recorded[key] == metrics[key]
    with (folder / "predictions_test.csv").open() as stream:
        predictions = list(csv.DictReader(stream))
    assert [r["path"] for r in predictions] == [r["path"] for r in split_rows(manifest, "test")]
