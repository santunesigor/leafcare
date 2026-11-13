"""App contract mirrored in Python: decoding, distilled preprocessing, ranking and config."""
import hashlib
from pathlib import Path

import numpy as np
import pytest
from PIL import Image

from leafcare.common import classes_hash, load_config
from leafcare.inference import rank
from leafcare.preprocessing import decode_rgb, preprocess, resize_distilled_rgb

REPO = Path(__file__).resolve().parents[2]


def test_transparency_is_composited_on_white(tmp_path):
    path = tmp_path / "transparent.png"
    Image.new("RGBA", (1, 1), (0, 100, 200, 128)).save(path)
    np.testing.assert_array_equal(decode_rgb(path)[0, 0], [127, 177, 227])


@pytest.mark.parametrize("orientation,expected", [(2, [[2, 1], [4, 3]]), (3, [[4, 3], [2, 1]]),
                                                  (5, [[1, 3], [2, 4]]), (6, [[3, 1], [4, 2]]),
                                                  (7, [[4, 2], [3, 1]]), (8, [[2, 4], [1, 3]])])
def test_exif_is_respected(tmp_path, orientation, expected):
    image = Image.fromarray(np.repeat(np.array([[1, 2], [3, 4]], np.uint8)[..., None], 3, 2))
    exif = Image.Exif()
    exif[274] = orientation
    path = tmp_path / "oriented.png"
    image.save(path, exif=exif)
    np.testing.assert_array_equal(decode_rgb(path)[..., 0], expected)


def test_pillow_resize_matches_the_android_golden_file():
    fixture = REPO / "android-app/app/src/test/resources/distilled_preprocess_golden.properties"
    props = dict(line.split("=", 1) for line in fixture.read_text().splitlines() if line and not line.startswith("#"))
    for name in props["cases"].split(","):
        width, height = map(int, props[f"{name}.size"].split(","))
        y, x = np.indices((height, width))
        rgb = np.stack([(x * 37 + y * 13) % 256, (x * 11 + y * 43) % 256, (x * 53 + y * 7) % 256], axis=-1)
        if name == "alpha":
            a = ((x * 17 + y * 31) % 256)[..., None]
            rgb = (rgb * a + 255 * (255 - a) + 127) // 255
        result = resize_distilled_rgb(rgb.astype(np.uint8))
        assert result.shape == (224, 224, 3)
        assert hashlib.sha256(result.astype(np.uint8).tobytes()).hexdigest() == props[f"{name}.sha256"]


def test_actual_reference_image_can_be_preprocessed():
    tensor = preprocess(REPO / "samples/reference_frog_eye.jpg")
    assert tensor.shape == (1, 224, 224, 3) and tensor.dtype == np.float32
    assert np.isfinite(tensor).all() and 0 <= tensor.min() <= tensor.max() <= 255


def test_stable_ranking_no_renormalization():
    result = rank([.1, .4, .4, .1], ["a", "b", "c", "d"], .7)
    assert result["inconclusive"]
    assert [r["class_id"] for r in result["top3"]] == ["b", "c", "a"]
    assert sum(r["confidence"] for r in result["top3"]) == pytest.approx(.9)


def test_threshold_exact_boundary():
    assert not rank(np.array([.7, .2, .1], np.float32), ["a", "b", "c"], .7)["inconclusive"]


@pytest.mark.parametrize("scores", [[float("nan"), 0, 1], [2, 0, 0], [.1, .1, .1], [-.1, .1, 1]])
def test_invalid_probability_vectors_rejected(scores):
    with pytest.raises(ValueError):
        rank(scores, ["a", "b", "c"], .7)


def test_hash_is_order_sensitive():
    assert classes_hash(["a", "b", "c"]) != classes_hash(["b", "a", "c"])


def test_repository_config_is_valid_and_rejects_bad_ratios(tmp_path):
    config = load_config()
    assert list(config["field_split"]) == ["train", "validation", "field_test"]
    bad = tmp_path / "config.yaml"
    bad.write_text((Path(__file__).resolve().parents[1] / "config.yaml").read_text().replace("0.70", "0.60"))
    with pytest.raises(ValueError, match="somar 1"):
        load_config(bad)
