"""End-to-end smoke test of retraining.train on synthetic images (CPU, ~minutes).

Opt-in: LEAFCARE_TRAINING_SMOKE=1, inside the training environment
(experiments/distillation/requirements.txt). Downloads the pinned MobileNetV4
Small ImageNet weights on first use. The teacher has random weights: this checks
the pipeline, never model quality.
"""
import json
import os
import tempfile
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path

import numpy as np
import pytest
from PIL import Image

pytestmark = pytest.mark.training

ROOT = Path(__file__).resolve().parents[1]


def synthetic_cycle(base: Path) -> Path:
    """16 classes, TLA and field photos, every split; returns runs-like folder with dataset.json."""
    from leafcare.common import read_json, sha256
    from leafcare.dataset import difference_hash
    from retraining.dataset import SCHEMA

    classes = read_json(ROOT / "artifacts/classes.json")
    rng = np.random.default_rng(0)
    (base / "tla").mkdir()
    (base / "field").mkdir()
    rows = []
    for c, label in enumerate(classes):
        for k, split in enumerate(["train", "train", "validation", "test"]):
            source = "field" if k == 1 and c % 2 else "tla"
            rgb = np.clip(rng.normal(30 + c * 12, 40, (96, 128, 3)), 0, 255).astype(np.uint8)
            name = f"{label}_{k}.jpg"
            Image.fromarray(rgb).save(base / source / name, quality=95)
            rows.append({"path": name, "source": source, "class_id": label, "sha256": sha256(base / source / name),
                         "pixel_sha256": name, "width": 128, "height": 96, "dhash": difference_hash(rgb),
                         "group_id": f"g{c}", "split": "field_test" if source == "field" and c % 4 == 1 else split,
                         "predicted_class_id": label})
    now = datetime.now(timezone.utc)
    run = base / "run"
    run.mkdir()
    dataset = {"schema": SCHEMA, "classes": classes, "classes_sha256": "x", "base_manifest_sha256": "synthetic",
               "roots": {"tla": str(base / "tla"), "field": str(base / "field")},
               "field_export": {"exported_at": now.isoformat(), "delete_after": (now + timedelta(days=30)).isoformat()},
               "production_model_on_field_test": {"n_images": 4, "top1_correct": 4, "top1_accuracy": 1.0},
               "rows": rows}
    (run / "dataset.json").write_text(json.dumps(dataset))
    return run


@unittest.skipUnless(os.environ.get("LEAFCARE_TRAINING_SMOKE") == "1", "set LEAFCARE_TRAINING_SMOKE=1 to run")
class RetrainingSmokeTest(unittest.TestCase):
    def test_distilled_cycle_produces_a_non_promotable_candidate(self):
        import torch
        from safetensors.torch import save_file

        from experiments.distillation import benchmark as recipe
        from experiments.vision.finetune import Classifier
        from leafcare.common import read_json
        from retraining.train import train

        classes = read_json(ROOT / "artifacts/classes.json")
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory)
            run = synthetic_cycle(base)
            rows = read_json(run / "dataset.json")["rows"]
            teacher = Classifier(recipe.DinoTokens(), np.zeros((16, 1536)), np.zeros(16))
            save_file({k: v.contiguous() for k, v in teacher.state_dict().items()}, str(base / "teacher.safetensors"))
            del teacher
            torch.set_num_threads(2)
            summary = train(run / "dataset.json", base / "teacher.safetensors", 1, 1, True, "cpu")

            self.assertEqual(summary["status"], "completed")
            self.assertEqual(summary["method"], "distilled")
            self.assertFalse(summary["promotable"])
            self.assertFalse(summary["recommended_for_promotion"])
            for name in ("checkpoint.safetensors", "initial_probe.npz", "teacher_targets.npy", "experiment_config.json",
                         "validation_metrics.json", "test_metrics.json", "field_test_metrics.json", "comparison.json",
                         "predictions_field_test.csv", "per_class_metrics.csv"):
                self.assertTrue((run / name).is_file(), name)
            config = read_json(run / "experiment_config.json")
            self.assertEqual(config["class_order"], classes)
            self.assertFalse(config["teacher"]["matches_recorded_teacher"])
            self.assertEqual(read_json(run / "teacher.json")["shape"], [2, sum(r["split"] == "train" for r in rows), 16])
            self.assertIn("field_test_top1", read_json(run / "comparison.json"))

    def test_candidate_converts_to_tflite_and_replays_with_app_preprocessing(self):
        pytest.importorskip("litert_torch", reason="export environment (requirements-export.txt)")
        import torch
        from leafcare.common import read_json
        from retraining import export_model, promote
        from retraining.train import train

        with tempfile.TemporaryDirectory() as directory:
            run = synthetic_cycle(Path(directory))
            torch.set_num_threads(2)
            train(run / "dataset.json", None, 1, 1, True, "cpu")
            exported = export_model.export(run)
            self.assertTrue(exported["parity"]["passed"])
            self.assertEqual(exported["output_shape"], [1, 16])
            config, summary, export, results = promote.replay(run)
            self.assertEqual(set(results), {"validation", "test", "field_test"})
            self.assertLessEqual(max(r["app_preprocessing_max_abs_error"] for r in results.values()), 1e-4)
            with self.assertRaisesRegex(ValueError, "fumaça"):
                promote.install(run, "never", config, summary, export, results, True)
            self.assertEqual(read_json(run / "run_summary.json")["method"], "supervised")
