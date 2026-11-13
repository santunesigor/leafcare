"""Cycle dataset: fixed TLA split, stable per-person field split, dedupe and holdout threshold."""
import json
from datetime import datetime, timezone

import pytest
from PIL import Image

from fakes import CONFIG, FakeSupabase, jpeg, picture, row
from leafcare.common import read_json, sha256
from training import dataset as cycle
from training import sync
from training.dataset import difference_hash, field_split

NOW = datetime(2026, 11, 12, tzinfo=timezone.utc)
CLASSES = read_json(cycle.ROOT / "artifacts/classes.json")


@pytest.fixture
def world(tmp_path):
    """A two-image TLA manifest on disk and an empty mirror."""
    tla = tmp_path / "raw"
    rows = []
    for i, (label, split) in enumerate([("frog_eye", "train"), ("healthy", "test")]):
        rgb = picture(50000 + i)
        (tla / label).mkdir(parents=True)
        path = tla / label / "a.png"
        Image.fromarray(rgb).save(path)
        rows.append({"path": f"{label}/a.png", "class_id": label, "sha256": sha256(path), "pixel_sha256": f"tla-{i}",
                     "width": 160, "height": 144, "dhash": difference_hash(rgb), "group_id": f"g{i}", "split": split})
    base = tmp_path / "manifest.json"
    base.write_text(json.dumps({"seed": 42, "classes": CLASSES, "classes_sha256": "x", "rows": rows}))
    return {"base": base, "tla": tla, "mirror": tmp_path / "field", "photos": {}, "rows": []}


def add(world, people, per_person=2, label="frog_eye", start=0):
    for p in range(start, start + people):
        for k in range(per_person):
            name = f"a{p}-{k}"
            world["rows"].append(row(name, f"user{p}", label=CLASSES[(p + k) % 16] if label is None else label))
            world["photos"][name] = jpeg(picture(p * 100 + k))


def build(world, config=CONFIG):
    sync.sync(FakeSupabase(world["rows"], world["photos"]), config, world["mirror"], NOW)
    return cycle.build(config, world["base"], world["mirror"], world["tla"], NOW)


def field(dataset):
    return {r["path"]: r for r in dataset["rows"] if r["source"] == "field"}


def test_tla_split_is_fixed_and_people_stay_in_one_split(world):
    add(world, 12, label=None)
    dataset = build(world)
    assert [r["split"] for r in dataset["rows"] if r["source"] == "tla"] == ["train", "test"]
    photos = field(dataset)
    assert len(photos) == 24
    by_group = {}
    for r in photos.values():
        by_group.setdefault(r["group_id"], set()).add(r["split"])
    assert len(by_group) == 12 and all(len(s) == 1 for s in by_group.values())
    assert {r["split"] for r in photos.values()} <= {"train", "validation", "field_test"}
    assert "test" not in {r["split"] for r in photos.values()}, "field photos never enter the TLA test"
    assert dataset["field_groups"] == 12 and not any("grupo" in w for w in dataset["warnings"])


def test_split_of_existing_people_is_stable_when_photos_are_added(world):
    add(world, 8)
    first = field(build(world))
    add(world, 6, start=8)  # new people
    for p in range(3):  # more photos from people already in the dataset
        world["rows"].append(row(f"extra{p}", f"user{p}"))
        world["photos"][f"extra{p}"] = jpeg(picture(9000 + p))
    second = field(build(world))
    assert len(second) == len(first) + 12 + 3
    for path, r in first.items():
        assert second[path]["split"] == r["split"] and second[path]["group_id"] == r["group_id"]
    splits = {r["group_id"]: r["split"] for r in second.values()}
    assert all(splits[r["group_id"]] == r["split"] for r in second.values())
    assert len(splits) == 14


def test_field_photos_repeating_tla_are_dropped(world):
    add(world, 6)
    world["rows"].append(row("copy", "user99"))
    world["photos"]["copy"] = jpeg(picture(50000))  # same pixels as the TLA frog_eye image (after JPEG: near-duplicate)
    dataset = build(world)
    assert dataset["rejected"] == {"near_duplicate_of_tla": 1}
    assert len(field(dataset)) == 12


def test_below_the_group_threshold_everything_trains(world):
    add(world, 4)
    dataset = build(world)
    assert {r["split"] for r in field(dataset).values()} == {"train"}
    assert dataset["field_ratios"] == {"train": 1.0}
    assert any("grupo" in w for w in dataset["warnings"])
    add(world, 1, start=4)
    dataset = build(world)
    assert dataset["field_ratios"] == CONFIG["field_split"]
    assert all(r["split"] == field_split(r["group_id"], 42, CONFIG["field_split"]) for r in field(dataset).values())


def test_near_duplicates_of_different_people_share_a_group(world):
    add(world, 5)
    twin = picture(0)  # user0's first photo with one pixel changed: near-duplicate, not identical
    twin[0, 0] = 255 - twin[0, 0]
    world["rows"].append(row("twin", "user77"))
    world["photos"]["twin"] = jpeg(twin)
    dataset = build(world)
    sizes = {}
    for r in field(dataset).values():
        sizes[r["group_id"]] = sizes.get(r["group_id"], 0) + 1
    assert dataset["field_groups"] == 5 and sorted(sizes.values()) == [2, 2, 2, 2, 3], "user77 joins user0's group"


def test_split_function_is_deterministic_and_covers_all_splits():
    ratios = CONFIG["field_split"]
    assert field_split("abc", 42, ratios) == field_split("abc", 42, ratios)
    assert {field_split(f"group-{i}", 42, ratios) for i in range(200)} == {"train", "validation", "field_test"}


def test_cycle_refuses_to_continue_after_the_mirror_changed(world, tmp_path):
    add(world, 6)
    run = tmp_path / "run"
    run.mkdir()
    (run / "dataset.json").write_text(json.dumps(build(world)))
    assert cycle.load_dataset(run / "dataset.json", NOW)[0]["field_groups"] == 6
    world["rows"][0] = row(world["rows"][0]["analysis_id"], "user0", label="healthy", status="corrected")
    sync.sync(FakeSupabase(world["rows"], world["photos"]), CONFIG, world["mirror"], NOW)
    with pytest.raises(ValueError, match="novo ciclo"):
        cycle.load_dataset(run / "dataset.json", NOW)
