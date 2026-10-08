import numpy as np
import pytest
from experiments.distillation import benchmark as b
from experiments.distillation.export import check_parity


def test_parity_rejects_changed_class_or_probability_or_training_reference():
    reference = np.array([[.7, .2, .1], [.1, .3, .6]])
    assert check_parity(reference, reference, reference)['passed']
    assert not check_parity(reference, reference[:, ::-1], reference)['passed']
    changed = reference.copy()
    changed[0, 0] += .001
    assert not check_parity(reference, changed, reference)['passed']
    assert not check_parity(reference, reference, changed)['passed']


def test_completed_exports_replay_probabilities_and_preserve_local_contract():
    exports = list(b.OUTPUT.glob('*/mobile_export.json'))
    completed = [p for p in exports if b.read_json(p)['status'] == 'completed']
    if not completed:
        pytest.skip('No completed export yet')
    for path in completed:
        metadata = b.read_json(path)
        cfg = b.read_json(path.parent / 'experiment_config.json')
        model_file = b.ROOT / metadata['path_local']
        assert model_file.resolve().is_relative_to(b.CACHE.resolve())
        assert model_file.stat().st_size == metadata['bytes']
        assert b.digest(model_file) == metadata['sha256']
        assert metadata['source_checkpoint_sha256'] == cfg['checkpoint_sha256']
        assert metadata['class_order'] == cfg['class_order']
        assert metadata['output_shape'] == [1, 16]
        assert metadata['input_shape'] == [1, *cfg['input_shape'][-2:], 3]
        assert metadata['experimental_only'] and not metadata['android_target_verified']
        with np.load(path.parent / 'conversion_probabilities.npz', allow_pickle=False) as scores:
            assert scores['reference'].shape == (104, 16)
            replay = check_parity(scores['reference'], scores['tflite'], scores['recorded'])
            assert replay['passed']
            for key, value in replay.items():
                assert metadata['parity'][key] == value
