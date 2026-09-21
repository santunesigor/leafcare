import hashlib
import json
from pathlib import Path
import numpy as np
import pytest
from PIL import Image
from leafcare.common import classes_hash, load_config
from leafcare.preprocessing import preprocess, decode_rgb, resize_rgb
from leafcare.inference import rank


def test_constant_image_remains_constant():
    pixels = np.full((51, 99, 3), (10, 120, 250), dtype=np.uint8)
    expected = np.broadcast_to(np.array([10, 120, 250]), (224, 224, 3))
    np.testing.assert_array_equal(resize_rgb(pixels), expected)


def test_center_crop_excludes_outer_columns():
    pixels = np.zeros((2, 6, 3), np.uint8)
    pixels[:, 2:4] = 130
    assert (resize_rgb(pixels) == 130).all()


def test_known_bilinear_interpolation():
    pixels = np.repeat(np.array([[0, 100], [100, 200]], dtype=np.uint8)[..., None], 3, axis=2)
    expected = np.array([[0, 25, 75, 100], [25, 50, 100, 125], [75, 100, 150, 175], [100, 125, 175, 200]])
    np.testing.assert_array_equal(resize_rgb(pixels, 4)[..., 0], expected)


def test_transparency_is_composited_on_white(tmp_path):
    path = tmp_path / "transparent.png"
    Image.new("RGBA", (1, 1), (0, 100, 200, 128)).save(path)
    np.testing.assert_array_equal(decode_rgb(path)[0, 0], [127, 177, 227])


@pytest.mark.parametrize("orientation,expected", [(2, [[2, 1], [4, 3]]), (3, [[4, 3], [2, 1]]),
                                                  (5, [[1, 3], [2, 4]]), (6, [[3, 1], [4, 2]]),
                                                  (7, [[4, 2], [3, 1]]), (8, [[2, 4], [1, 3]])])
def test_exif_is_respected(tmp_path, orientation, expected):
    image = Image.fromarray(np.repeat(np.array([[1, 2], [3, 4]], np.uint8)[..., None], 3, 2))
    exif = Image.Exif(); exif[274] = orientation
    path = tmp_path / "oriented.png"; image.save(path, exif=exif)
    np.testing.assert_array_equal(decode_rgb(path)[..., 0], expected)


def test_actual_reference_image_can_be_preprocessed():
    image = Path(__file__).resolve().parents[2] / "samples/reference_frog_eye.jpg"
    tensor = preprocess(image)
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
    with pytest.raises(ValueError): rank(scores, ["a", "b", "c"], .7)


def test_hash_is_order_sensitive():
    assert classes_hash(["a", "b", "c"]) != classes_hash(["b", "a", "c"])


def test_shared_golden_fixture():
    root = Path(__file__).resolve().parents[2]
    fixture = json.loads((root / "samples/preprocess_golden.json").read_text())
    rgba = np.array(fixture["rgba"], dtype=np.uint32).reshape(fixture["height"], fixture["width"], 4)
    a = rgba[..., 3:4]
    rgb = ((rgba[..., :3] * a + 255 * (255 - a) + 127) // 255).astype(np.uint8)
    result = resize_rgb(rgb).astype(np.uint8)
    assert hashlib.sha256(result.tobytes()).hexdigest() == fixture["resized_rgb_sha256"]
