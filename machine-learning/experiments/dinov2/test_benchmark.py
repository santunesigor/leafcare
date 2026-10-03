import json
from pathlib import Path

import numpy as np
import pytest

from experiments.dinov2.benchmark import (
    classification_metrics, encode_labels, split_rows, validate_manifest,
)


ROOT = Path(__file__).resolve().parents[2]


def test_fixed_manifest_class_order_counts_and_groups():
    manifest = json.loads((ROOT / "data/prepared/manifest.json").read_text(encoding="utf-8"))
    classes = validate_manifest(manifest)
    app_classes = json.loads((ROOT / "artifacts/classes.json").read_text(encoding="utf-8"))
    assert classes == app_classes
    assert len(classes) == 16
    assert [len(split_rows(manifest, split)) for split in ("train", "validation", "test")] == [489, 104, 103]
    assert not ({row["group_id"] for row in split_rows(manifest, "train")} &
                {row["group_id"] for row in split_rows(manifest, "test")})


def test_label_mapping_and_split_output_order_are_manifest_order():
    rows = [{"path": "b.jpg", "class_id": "b", "split": "train"},
            {"path": "a.jpg", "class_id": "a", "split": "train"}]
    assert encode_labels(rows, ["a", "b"]).tolist() == [1, 0]
    manifest = {"rows": rows}
    assert [row["path"] for row in split_rows(manifest, "train")] == ["b.jpg", "a.jpg"]


def test_validation_metrics_are_deterministic_and_top3_uses_stable_order():
    classes = [f"class_{i}" for i in range(16)]
    truth = np.asarray([0, 1, 2])
    logits = np.zeros((3, 16))
    logits[0, [0, 1, 2]] = [1, .5, .25]
    logits[1, [0, 1, 2]] = [.5, 1, .25]
    logits[2, [0, 1, 2]] = [1, .5, .25]
    first = classification_metrics(truth, logits, classes)
    second = classification_metrics(truth, logits, classes)
    assert first == second
    assert first["top1_correct"] == 2
    assert first["top3_correct"] == 3


def test_manifest_rejects_group_leakage():
    manifest = {
        "classes": [f"class_{i}" for i in range(16)],
        "rows": [
            {"path": f"{split}/{i}", "class_id": f"class_{i}", "split": split,
             "group_id": f"{split}-{i}", "pixel_sha256": f"hash-{split}-{i}"}
            for split in ("train", "validation", "test") for i in range(16)
        ],
    }
    manifest["rows"][-1]["group_id"] = manifest["rows"][0]["group_id"]
    with pytest.raises(ValueError, match="Vazamento"):
        validate_manifest(manifest, {"train": 16, "validation": 16, "test": 16})
