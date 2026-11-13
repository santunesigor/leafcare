import numpy as np
import pytest

pytest.importorskip("torch")
pytest.importorskip("safetensors")
from training.export import acceptance_flips  # noqa: E402


def probs(*confidences):
    return np.array([[c, (1 - c) / 2, (1 - c) / 2] for c in confidences], dtype=np.float32)


def test_borderline_confidence_may_change_acceptance():
    threshold = np.float32(0.5)
    flipped = acceptance_flips(probs(0.50001, 0.9), probs(0.49999, 0.9), threshold)
    assert flipped.tolist() == [True, False]


def test_acceptance_change_far_from_threshold_is_rejected():
    with pytest.raises(ValueError):
        acceptance_flips(probs(0.6), probs(0.4), np.float32(0.5))
