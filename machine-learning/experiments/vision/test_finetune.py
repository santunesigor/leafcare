import json

import numpy as np
import pytest
import torch

from experiments.vision.finetune import Classifier, OUTPUT, PUBLIC_MODELS, configure_trainable, validation_key


def test_training_rerun_invalidates_previous_mobile_export(tmp_path, monkeypatch):
    from experiments.vision import finetune
    folder = tmp_path / "tinyvit_5m"
    folder.mkdir()
    (folder / "mobile_export.json").write_text('{"status": "completed"}')
    monkeypatch.setattr(finetune, "OUTPUT", tmp_path)

    def unavailable(*args, **kwargs):
        raise RuntimeError("unavailable checkpoint")

    monkeypatch.setattr(finetune, "load_encoder", unavailable)
    with pytest.raises(RuntimeError, match="unavailable checkpoint"):
        finetune.run("tinyvit_5m", {}, {}, [], torch.device("cpu"))
    assert json.loads((folder / "run_summary.json").read_text())["status"] == "running"
    assert json.loads((folder / "mobile_export.json").read_text())["status"] == "invalidated_by_training"


def test_warmup_freezes_encoder_and_only_updates_classifier():
    encoder = torch.nn.Linear(4, 3)
    model = Classifier(encoder, np.ones((16, 3)), np.zeros(16))
    before = encoder.weight.detach().clone()
    configure_trainable(model, "tinyvit_5m", False)
    optimizer = torch.optim.SGD([p for p in model.parameters() if p.requires_grad], lr=.01)
    torch.nn.functional.cross_entropy(model(torch.rand(2, 4)), torch.tensor([0, 1])).backward()
    assert encoder.weight.grad is None
    assert model.head.weight.grad is not None
    optimizer.step()
    torch.testing.assert_close(encoder.weight, before)


def test_dino_finetuning_limits_gradients_to_last_two_blocks_and_norm():
    class Backbone(torch.nn.Module):
        def __init__(self):
            super().__init__()
            self.blocks = torch.nn.ModuleList([torch.nn.Linear(4, 4) for _ in range(4)])
            self.norm = torch.nn.LayerNorm(4)

        def forward(self, x):
            for block in self.blocks:
                x = torch.relu(block(x))
            return self.norm(x)

    class Encoder(torch.nn.Module):
        def __init__(self):
            super().__init__()
            self.backbone = Backbone()

        def forward(self, x):
            return self.backbone(x)

    model = Classifier(Encoder(), np.random.default_rng(42).normal(size=(16, 4)), np.zeros(16))
    configure_trainable(model, "dinov2_vitb14", True)
    torch.nn.functional.cross_entropy(model(torch.rand(3, 4)), torch.tensor([0, 1, 2])).backward()
    assert all(p.grad is None for block in model.encoder.backbone.blocks[:2] for p in block.parameters())
    assert all(p.grad is not None for block in model.encoder.backbone.blocks[-2:] for p in block.parameters())
    assert model.encoder.backbone.norm.weight.grad is not None


def test_validation_selection_ignores_test_results():
    good_val = {"macro_f1": .8, "top1_accuracy": .85, "top3_accuracy": .95, "test_accuracy": .1}
    bad_val = {"macro_f1": .7, "top1_accuracy": .9, "top3_accuracy": 1., "test_accuracy": 1.}
    assert validation_key(good_val) > validation_key(bad_val)


@pytest.mark.parametrize("model_id", PUBLIC_MODELS)
def test_completed_finetuning_metrics_replay_and_epoch_selection(model_id):
    from experiments.vision.benchmark import ROOT, encode_labels, evaluated_metrics, split_rows
    folder = OUTPUT / model_id
    summary_file = folder / "run_summary.json"
    if not summary_file.exists() or json.loads(summary_file.read_text())["status"] != "completed":
        pytest.skip("Training not completed")
    config = json.loads((folder / "experiment_config.json").read_text())
    history = json.loads((folder / "history.json").read_text())
    best = max(history, key=lambda row: validation_key(row["validation"]))
    assert config["selected_epoch"] == best["epoch"]
    assert config["selected_stage"] == best["stage"]
    assert config["test_used_for_selection"] is False
    manifest = json.loads((ROOT / "data/prepared/manifest.json").read_text())
    with np.load(folder / "logits.npz", allow_pickle=False) as scores:
        for split in ("validation", "test"):
            truth = encode_labels(split_rows(manifest, split), manifest["classes"])
            metrics, _ = evaluated_metrics(truth, scores[split], manifest["classes"], config["temperature"], config["threshold"])
            recorded = json.loads((folder / f"{split}_metrics.json").read_text())
            for key in ("top1_accuracy", "macro_f1", "top3_accuracy", "coverage", "accepted_accuracy"):
                assert metrics[key] == recorded[key]
