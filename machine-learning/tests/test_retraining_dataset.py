import json
import tempfile
import unittest
from datetime import datetime, timezone
from pathlib import Path

import numpy as np
from PIL import Image

from leafcare.common import sha256
from leafcare.dataset import difference_hash
from retraining.dataset import build, field_split

NOW = datetime(2026, 11, 12, tzinfo=timezone.utc)
CLASSES = ["brown_spot", "frog_eye", "healthy"]


def pattern(seed):
    rng = np.random.default_rng(seed)
    return (rng.random((160, 180, 3)) * 255).astype(np.uint8)


class RetrainingDatasetTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        root = Path(self.directory.name)
        self.base = root / "manifest.json"
        tla_rgb = pattern(0)
        self.tla_dhash = difference_hash(tla_rgb)
        self.base.write_text(json.dumps({"seed": 42, "classes": CLASSES, "classes_sha256": "x", "rows": [
            {"path": "frog_eye/a.jpg", "class_id": "frog_eye", "sha256": "s", "pixel_sha256": "tla-pixels",
             "width": 180, "height": 160, "dhash": self.tla_dhash, "group_id": "g0", "split": "train"},
            {"path": "healthy/b.jpg", "class_id": "healthy", "sha256": "s", "pixel_sha256": "tla-2",
             "width": 180, "height": 160, "dhash": difference_hash(pattern(99)), "group_id": "g1", "split": "test"}]}))
        self.export = root / "export"
        self.export.mkdir()

    def write_export(self, images, exported_at="2026-11-10T00:00:00+00:00", delete_after="2026-12-10T00:00:00+00:00"):
        rows = []
        for i, (group, label, rgb) in enumerate(images):
            name = f"{i:04d}.jpg"
            Image.fromarray(rgb).save(self.export / name, quality=95)
            rows.append({"image": name, "class_id": label, "review_status": "confirmed", "predicted_class_id": "frog_eye",
                         "group_id": group, "sha256": sha256(self.export / name), "pixel_sha256": f"p{i}",
                         "width": 180, "height": 160, "dhash": difference_hash(rgb)})
        (self.export / "manifest.json").write_text(json.dumps({"schema_version": 2, "exported_at": exported_at,
                                                               "delete_after": delete_after, "images": rows}))

    def test_tla_split_is_kept_and_field_groups_stay_together(self):
        images = [(f"group{g}", "frog_eye" if g % 2 else "brown_spot", pattern(10 + g * 10 + k)) for g in range(8) for k in range(2)]
        self.write_export(images)
        dataset = build(self.base, self.export, now=NOW)
        tla = [r for r in dataset["rows"] if r["source"] == "tla"]
        self.assertEqual([r["split"] for r in tla], ["train", "test"])
        field = [r for r in dataset["rows"] if r["source"] == "field"]
        self.assertEqual(len(field), 16)
        by_group = {}
        for row in field:
            by_group.setdefault(row["group_id"], set()).add(row["split"])
        self.assertTrue(all(len(splits) == 1 for splits in by_group.values()))
        self.assertTrue(set(r["split"] for r in field) <= {"train", "validation", "field_test"})
        self.assertNotIn("test", {r["split"] for r in field}, "field photos never enter the historical TLA test")

    def test_few_owners_train_only_with_warning(self):
        self.write_export([("only", "frog_eye", pattern(1)), ("only", "healthy", pattern(2))])
        dataset = build(self.base, self.export, now=NOW)
        self.assertEqual({r["split"] for r in dataset["rows"] if r["source"] == "field"}, {"train"})
        self.assertTrue(any("grupo" in warning for warning in dataset["warnings"]))
        self.assertIsNone(dataset["production_model_on_field_test"])

    def test_rejects_tla_repeats_unknown_classes_and_changed_files(self):
        self.write_export([("a", "frog_eye", pattern(0)), ("b", "unknown", pattern(3)), ("c", "healthy", pattern(4))])
        (self.export / "0002.jpg").write_bytes(b"changed")
        dataset = build(self.base, self.export, now=NOW)
        self.assertEqual(dataset["rejected"], {"near_duplicate_of_tla": 1, "unknown_class": 1, "missing_or_changed": 1})

    def test_expired_export_cannot_train(self):
        self.write_export([("a", "frog_eye", pattern(3))], delete_after="2026-11-11T00:00:00+00:00")
        with self.assertRaisesRegex(ValueError, "vencida"):
            build(self.base, self.export, now=NOW)

    def test_split_of_a_group_never_changes(self):
        self.assertEqual(field_split("abc", 42), field_split("abc", 42))
        splits = {field_split(f"group-{i}", 42) for i in range(200)}
        self.assertEqual(splits, {"train", "validation", "field_test"})

    def test_production_baseline_uses_recorded_prediction(self):
        images = [(f"group{g}", "frog_eye" if g % 2 else "healthy", pattern(100 + g)) for g in range(40)]
        self.write_export(images)
        dataset = build(self.base, self.export, now=NOW)
        field_test = [r for r in dataset["rows"] if r["split"] == "field_test"]
        baseline = dataset["production_model_on_field_test"]
        self.assertEqual(baseline["n_images"], len(field_test))
        self.assertEqual(baseline["top1_correct"], sum(r["class_id"] == "frog_eye" for r in field_test))


if __name__ == "__main__":
    unittest.main()
