"""Full retraining chain on synthetic images: sync → dataset → teacher → student → TFLite → replay → promote.

Opt-in (minutes on CPU): LEAFCARE_TRAINING_SMOKE=1, in either environment. In the
training environment the TFLite step runs through the export environment as a
subprocess, exactly like `retrain.py run`; in the export environment it runs inline.
The teacher is a tiny ViT with random weights and the student trains 1+1 epochs:
this checks the pipeline, never model quality. Downloads the pinned MobileNetV4
Small ImageNet weights to .cache/weights on first use. Nothing outside the
temporary directory is written.
"""
import importlib.util
import json
import os
import shutil
from pathlib import Path

import pytest

from fakes import CONFIG, FakeSupabase, jpeg, picture, row
from leafcare.common import ROOT, read_json, sha256

pytestmark = [pytest.mark.training,
              pytest.mark.skipif(os.environ.get("LEAFCARE_TRAINING_SMOKE") != "1", reason="LEAFCARE_TRAINING_SMOKE=1")]


def synthetic_world(base: Path):
    """TLA-like manifest with every class in train/validation/test, plus 10 app users with 2 photos each."""
    from PIL import Image

    from training.dataset import difference_hash
    classes = read_json(ROOT / "artifacts/classes.json")
    rows = []
    for c, label in enumerate(classes):
        for k, split in enumerate(["train", "train", "validation", "test"]):
            rgb = picture(10_000 + c * 10 + k)
            path = base / "raw" / label / f"{k}.png"
            path.parent.mkdir(parents=True, exist_ok=True)
            Image.fromarray(rgb).save(path)
            rows.append({"path": f"{label}/{k}.png", "class_id": label, "sha256": sha256(path),
                         "pixel_sha256": f"{label}-{k}", "width": 160, "height": 144, "dhash": difference_hash(rgb),
                         "group_id": f"{label}-{k}", "split": split})
    (base / "manifest.json").write_text(json.dumps({"seed": 42, "classes": classes, "classes_sha256": "x", "rows": rows}))
    pool = [row(f"a{p}-{k}", f"user{p}", label=classes[(p * 3 + k) % 16]) for p in range(10) for k in range(2)]
    photos = {r["analysis_id"]: jpeg(picture(20_000 + i)) for i, r in enumerate(pool)}
    return FakeSupabase(pool, photos)


def test_full_cycle_from_sync_to_promotion(tmp_path):
    import retrain
    from training import dataset as cycle
    from training import promote, sync, train

    client = synthetic_world(tmp_path)
    (tmp_path / "field").mkdir()
    (tmp_path / "field/.secret").write_text("5e" * 32)  # fixed pseudonyms: deterministic field split
    sync.sync(client, CONFIG, tmp_path / "field")
    dataset = cycle.build(CONFIG, tmp_path / "manifest.json", tmp_path / "field", tmp_path / "raw")
    assert dataset["field_counts"]["field_test"] > 0, "seeded groups must leave a field test"
    run = tmp_path / "runs/smoke"
    run.mkdir(parents=True)
    (run / "dataset.json").write_text(json.dumps(dataset))

    tiny = {**train.TEACHER, "name": "tiny random ViT (smoke test only)", "architecture": "vit_tiny_patch16_224",
            "revision": None, "pretrained_sha256": None}
    comparison = retrain.run_cycle(run, do_sync=False, device_name="cpu", teacher_spec=tiny, epochs=(1, 1),
                                   teachers=tmp_path / "teachers", smoke=True)

    assert not comparison["recommended_for_promotion"] and "fumaça" in comparison["reasons"][0]
    assert set(comparison["comparison"]) == {"test", "field_test"}
    teacher = read_json(next((tmp_path / "teachers").glob("*/teacher.json")))
    assert teacher["status"] == "completed" and teacher["epochs_executed"] == 2
    assert read_json(run / "teacher.json")["targets_shape"][:1] == [2]
    export = read_json(run / "mobile_export.json")
    assert export["parity"]["passed"] and export["output_shape"] == [1, 16]
    replayed = read_json(run / "replay.json")
    assert set(replayed["candidate"]) == {"validation", "test", "field_test"}
    assert set(replayed["production"]) == {"test", "field_test"}

    # Rerunning skips finished steps: the teacher and the candidate stay the same.
    checkpoint = read_json(run / "experiment_config.json")["checkpoint_sha256"]
    retrain.run_cycle(run, do_sync=False, device_name="cpu", teacher_spec=tiny, epochs=(1, 1),
                      teachers=tmp_path / "teachers", smoke=True)
    assert read_json(run / "experiment_config.json")["checkpoint_sha256"] == checkpoint

    with pytest.raises(ValueError, match="fumaça"):
        promote.install(run, "never", allow_unrecommended=True)
    repo = tmp_path / "repo"
    artifacts, assets = repo / "machine-learning/artifacts", repo / "android-app/app/src/main/assets"
    shutil.copytree(ROOT / "artifacts", artifacts)
    shutil.copytree(ROOT.parent / "android-app/app/src/main/assets", assets)
    metadata = promote.install(run, "smoke-model", allow_unrecommended=True, artifacts=artifacts, assets=assets,
                               reports=repo / "docs/model-reports", allow_smoke=True)
    assert sha256(assets / "leafcare.tflite") == metadata["model_sha256"] == export["sha256"]
    report = repo / "docs/model-reports/smoke-model"
    assert {"test_metrics.json", "field_test_metrics.json", "comparison.json", "dataset_summary.json"} <= {
        p.name for p in report.iterdir()}
    assert "rows" not in read_json(report / "dataset_summary.json")
    assert not any(p.suffix == ".csv" and p.name.startswith("predictions") for p in report.iterdir())
    if importlib.util.find_spec("ai_edge_litert"):
        from validate_bundle import validate
        assert validate(repo)["status"] == "model_bundle_valid"
