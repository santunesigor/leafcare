import numpy as np
import pytest
import torch
from PIL import Image
from experiments.distillation import benchmark as b


def test_teacher_frozen_and_kl_matches_definition():
    student = torch.tensor([[1., -1., 0.], [0., 2., -2.]], requires_grad=True)
    teacher = torch.tensor([[3., 1., -1.], [-1., 3., 0.]], requires_grad=True)
    labels, weights = torch.tensor([0, 1]), torch.ones(3)
    loss, _, kl = b.kd_loss(student, teacher, labels, weights, True)
    t = b.RECIPE['distillation_temperature']
    p = (teacher.detach() / t).softmax(1)
    expected_kl = (p * (p.log() - (student / t).log_softmax(1))).sum(1).mean() * t**2
    torch.testing.assert_close(kl, expected_kl.detach())
    torch.testing.assert_close(loss, .5 * torch.nn.functional.cross_entropy(student, labels) + .5 * expected_kl)
    loss.backward()
    assert teacher.grad is None
    assert student.grad is not None and torch.isfinite(student.grad).all()


def test_supervised_does_not_need_teacher():
    scores, labels, weights = torch.randn(3, 16, requires_grad=True), torch.tensor([0, 1, 2]), torch.ones(16)
    loss, _, kl = b.kd_loss(scores, None, labels, weights, False)
    torch.testing.assert_close(loss, torch.nn.functional.cross_entropy(scores, labels))
    assert kl.item() == 0


def test_augmentation_reproducible_without_changing_training_rng():
    image = Image.fromarray(np.random.default_rng(7).integers(0, 256, (48, 48, 3), dtype=np.uint8))
    torch.manual_seed(8)
    state = torch.random.get_rng_state().clone()
    cuda_state = torch.cuda.get_rng_state().clone() if torch.cuda.is_available() else None
    first = np.asarray(b.augmented(image, 1, 3))
    assert torch.equal(state, torch.random.get_rng_state())
    if cuda_state is not None:
        assert torch.equal(cuda_state, torch.cuda.get_rng_state())
    assert np.array_equal(first, np.asarray(b.augmented(image, 1, 3)))
    assert not np.array_equal(first, np.asarray(b.augmented(image, 2, 3)))


def test_only_training_can_be_augmented_or_distilled():
    manifest = {'classes': list(range(16)), 'rows': []}
    for split in ('validation', 'test'):
        with pytest.raises(ValueError, match='Only training'):
            b.Images({}, manifest, split, None, epoch=1)


def test_isolation_detects_changes_additions_and_removals(monkeypatch):
    monkeypatch.setattr(b, 'protected_hashes', lambda: {'model': 'new', 'added': 'hash'})
    with pytest.raises(ValueError, match='Protected files changed'):
        b.verify_protected({'model': 'old', 'removed': 'hash'})
    monkeypatch.setattr(b, 'protected_hashes', lambda: {'model': 'same'})
    assert b.verify_protected({'model': 'same'})['passed']


def test_results_replay_selection_and_paired_initialization():
    completed = [p for p in b.OUTPUT.glob('*_*/run_summary.json') if b.read_json(p)['status'] == 'completed']
    if not completed:
        pytest.skip('No completed experiment yet')
    manifest = b.read_json(b.ROOT / 'data/prepared/manifest.json')
    for path in completed:
        folder = path.parent
        config, history = b.read_json(folder / 'experiment_config.json'), b.read_json(folder / 'history.json')
        selected = max(history, key=lambda row: b.validation_key(row['validation']))
        assert config['selected_epoch'] == selected['epoch']
        assert config['test_used_for_selection'] is False
        assert b.digest(b.ROOT / config['checkpoint_local']) == config['checkpoint_sha256']
        with np.load(folder / 'logits.npz', allow_pickle=False) as scores:
            for split in ('validation', 'test'):
                truth = b.encode_labels(b.split_rows(manifest, split), manifest['classes'])
                replay, _ = b.evaluated_metrics(truth, scores[split], manifest['classes'], config['temperature'], config['threshold'])
                recorded = b.read_json(folder / (split + '_metrics.json'))
                for key in ('macro_f1', 'top1_accuracy', 'top3_accuracy', 'coverage', 'accepted_accuracy'):
                    assert replay[key] == recorded[key]
    for model_id in b.MODELS:
        pair = [b.OUTPUT / (model_id + '_' + method) / 'experiment_config.json' for method in b.METHODS]
        if all(p.exists() for p in pair):
            supervised, distilled = map(b.read_json, pair)
            for key in ('initial_state_sha256', 'initial_probe_sha256', 'pretrained_sha256', 'preprocessing', 'recipe'):
                assert supervised[key] == distilled[key]


def test_final_delivery_has_ten_runs_and_preserves_baseline():
    if not (b.OUTPUT / 'delivery_validation.json').exists():
        pytest.skip('Delivery not finalized yet')
    for model_id in b.MODELS:
        for method in b.METHODS:
            assert b.read_json(b.OUTPUT / f'{model_id}_{method}/run_summary.json')['status'] == 'completed'
    assert b.verify_protected(b.read_json(b.OUTPUT / 'isolation_before.json'))['passed']
