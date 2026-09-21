from copy import deepcopy
from pathlib import Path
import numpy as np
import pytest
from PIL import Image
from leafcare.dataset import audit_dataset, build_groups, stratified_group_split


def test_audit_invalid_duplicates_and_label_conflict(tmp_path):
    for label in ["b", "a", "c"]: (tmp_path / label).mkdir()
    image = Image.fromarray(np.random.default_rng(5).integers(0, 256, (10, 12, 3), np.uint8))
    for path in ["a/one.png", "a/copy.bmp", "b/conflict.png"]: image.save(tmp_path / path)
    (tmp_path / "c/broken.jpg").write_bytes(b"not a jpeg")
    rows, audit = audit_dataset(tmp_path)
    assert audit["classes"] == ["a", "b", "c"]
    assert len(audit["exact_duplicates_removed"]) == 1
    assert len(audit["label_conflicts"]) == 1
    assert len(audit["invalid_files"]) == 1
    assert len(rows) == 1


def make_rows():
    return [{"path": f"{c}/{i}.png", "class_id": c, "group_id": f"{c}-{i//2}"}
            for c in ["a", "b", "c"] for i in range(30)]


def test_stratified_group_split_is_reproducible_and_disjoint():
    ratios = {"train": .7, "validation": .15, "test": .15}
    rows = stratified_group_split(make_rows(), ["a", "b", "c"], ratios, 42)
    again = stratified_group_split(make_rows(), ["a", "b", "c"], ratios, 42)
    assert rows == again
    memberships = {}
    for row in rows:
        assert memberships.setdefault(row["group_id"], row["split"]) == row["split"]
    for split in ratios:
        assert {r["class_id"] for r in rows if r["split"] == split} == {"a", "b", "c"}
    assert sum(r["split"] == "train" for r in rows) > sum(r["split"] == "test" for r in rows)


def test_too_few_independent_groups_blocks_training():
    rows = [{"class_id": c, "group_id": c} for c in ["a", "b", "c"]]
    with pytest.raises(ValueError, match="insuficientes"):
        stratified_group_split(rows, ["a", "b", "c"], {"train": .7, "validation": .15, "test": .15}, 42)


def test_near_duplicates_join_even_across_labels():
    rows = [{"path": "a/1", "class_id": "a", "dhash": 0}, {"path": "b/2", "class_id": "b", "dhash": 1}]
    assert len(build_groups(rows, distance=1)) == 1
    assert rows[0]["group_id"] == rows[1]["group_id"]


def test_origin_groups_are_not_broken(tmp_path):
    rows = [{"path": "a/1", "class_id": "a", "dhash": 0}, {"path": "b/2", "class_id": "b", "dhash": 65535}]
    path = tmp_path / "groups.csv"; path.write_text("path,group_id\na/1,plant1\nb/2,plant1\n")
    build_groups(rows, path, 0)
    assert rows[0]["group_id"] == rows[1]["group_id"]


def test_incomplete_origin_metadata_rejected(tmp_path):
    path = tmp_path / "groups.csv"; path.write_text("path,group_id\na/1,plant1\n")
    with pytest.raises(ValueError, match="cobre"):
        build_groups([{"path": "a/2", "dhash": 0}], path)
