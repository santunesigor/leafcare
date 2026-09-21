import numpy as np

from benchmark_predict import calibrate


def test_calibration_preserves_probability_distribution_and_order():
    probabilities = np.array([0.70, 0.20, 0.10], dtype=np.float32)
    calibrated = calibrate(probabilities, 0.8421423931819505)
    assert calibrated.dtype == np.float32
    assert np.isclose(calibrated.sum(), 1.0)
    assert calibrated[0] > calibrated[1] > calibrated[2]
    assert calibrated[0] > probabilities[0]


def test_temperature_one_is_identity():
    probabilities = np.array([0.60, 0.25, 0.15], dtype=np.float32)
    assert np.allclose(calibrate(probabilities, 1.0), probabilities)
