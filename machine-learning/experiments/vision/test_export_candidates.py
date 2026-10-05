import json
import csv

import numpy as np
import pytest
import torch

from experiments.vision import export_candidates


def test_mobile_contract_embeds_rgb_normalization_and_calibrated_softmax():
    class Scores(torch.nn.Module):
        def forward(self, normalized):
            return normalized.mean(dim=(2, 3))

    rgb = torch.tensor([[[[0., 127.5, 255.]]]])
    mobile = export_candidates.MobileInput(Scores(), [.1, .2, .3], [.5, 1., 2.], 2.)
    expected = torch.softmax(torch.tensor([[-.2, .3, .35]]) / 2., dim=1)
    torch.testing.assert_close(mobile(rgb), expected)


def test_finalist_selection_uses_validation_and_requires_every_predeclared_run(tmp_path, monkeypatch):
    monkeypatch.setattr(export_candidates, "OUTPUT", tmp_path)
    (tmp_path / "protocol.json").write_text(json.dumps({"models": ["a", "b", "c"]}))
    for model, macro_f1, test_accuracy in (("a", .8, .1), ("b", .7, 1.), ("c", .9, .5)):
        folder = tmp_path / model
        folder.mkdir()
        (folder / "run_summary.json").write_text(json.dumps({
            "status": "completed", "validation": {"macro_f1": macro_f1, "top1_accuracy": .9, "top3_accuracy": 1.},
            "test": {"top1_accuracy": test_accuracy},
        }))
    assert export_candidates.select_finalists() == ["c", "a"]
    (tmp_path / "b" / "run_summary.json").write_text('{"status": "running"}')
    with pytest.raises(ValueError, match="must finish"):
        export_candidates.select_finalists()


@pytest.mark.parametrize("model_id", ["dinov2_vitb14", "tinyvit_11m"])
def test_completed_mobile_exports_preserve_validation_probabilities(model_id):
    folder = export_candidates.OUTPUT / model_id
    path = folder / "mobile_export.json"
    if not path.exists() or json.loads(path.read_text())["status"] != "completed":
        pytest.skip("Export not completed")
    metadata = json.loads(path.read_text())
    parity = json.loads((folder / "conversion_parity.json").read_text())
    with np.load(folder / "conversion_probabilities.npz", allow_pickle=False) as probabilities:
        assert probabilities["reference"].shape == (104, 16)
        np.testing.assert_array_equal(probabilities["reference"].argmax(axis=1), probabilities["tflite"].argmax(axis=1))
        assert np.abs(probabilities["reference"] - probabilities["tflite"]).max() <= parity["max_abs_error_limit"]
    assert parity["passed"] is True
    assert parity["recorded_training_top1_agreement"] == 1
    assert metadata["input_shape"] == [1, 224, 224, 3]
    assert metadata["android_target_verified"] is False
    assert metadata["android_full_analysis_ms"] is None


def test_comparison_csv_contains_all_training_runs_and_mobile_measurements():
    from experiments.vision.report import FIELDS, OUTPUT
    with (OUTPUT / "comparison.csv").open() as stream:
        reader = csv.DictReader(stream)
        assert reader.fieldnames == FIELDS
        rows = list(reader)
    assert len(rows) == 29
    adjusted = [row for row in rows if row["status"] == "finetune_completed"]
    assert len(adjusted) == 5
    exports = [row for row in adjusted if row["mobile_export_status"] == "completed"]
    assert len(exports) == 2
    assert all(float(row["mobile_top1_agreement"]) == 1 for row in exports)
    assert all(row["android_reference_device"] == "Samsung Galaxy A06" for row in exports)
    assert all(not row["android_latency_ms"] for row in rows)
    current = next(row for row in rows if row["id"] == "ensemble_integrated")
    assert current["status"] == current["mobile_export_status"] == "integrated"
    assert float(current["mobile_top1_agreement"]) == 1
    previous = next(row for row in rows if row["id"] == "baseline_anterior")
    assert previous["status"] == "previous_integrated"
    assert float(previous["test_top1"]) == pytest.approx(80 / 103)
