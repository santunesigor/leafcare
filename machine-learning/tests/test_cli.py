"""retrain.py wiring and the automatic promotion recommendation."""
import pytest

import retrain
from training.promote import recommendation


def parse(*argv):
    return retrain.parser().parse_args(list(argv))


def test_subcommands_are_wired():
    assert parse("setup").func is retrain.cmd_setup
    assert parse("status").func is retrain.cmd_status
    assert parse("sync").func is retrain.cmd_sync
    args = parse("run", "--name", "2026-12-retreino", "--teacher", "t.safetensors", "--no-sync", "--device", "cpu")
    assert (args.func, args.name, args.teacher.name, args.no_sync, args.device) == (
        retrain.cmd_run, "2026-12-retreino", "t.safetensors", True, "cpu")
    args = parse("promote", "--name", "c1", "--model-name", "mobilenetv4-2026-12", "--allow-unrecommended")
    assert (args.func, args.name, args.model_name, args.allow_unrecommended) == (
        retrain.cmd_promote, "c1", "mobilenetv4-2026-12", True)
    assert parse("run", "--name", "c1").no_sync is False


@pytest.mark.parametrize("argv", [["run"], ["run", "--name", "../escape"], ["promote", "--name", "c1"],
                                  ["promote", "--name", "c1", "--model-name", "a/b"], ["run", "--name", "c", "--device", "tpu"]])
def test_invalid_arguments_are_rejected(argv, capsys):
    with pytest.raises(SystemExit) as error:
        parse(*argv)
    assert error.value.code == 2


@pytest.mark.parametrize("command", [[], ["setup"], ["status"], ["sync"], ["run"], ["promote"]])
def test_help_is_in_portuguese_and_hides_the_internal_step(command, capsys):
    with pytest.raises(SystemExit) as error:
        parse(*command, "--help")
    assert error.value.code == 0
    text = capsys.readouterr().out
    assert "_export" not in text
    if not command:
        assert "Ponto de entrada único" in text or "ponto de entrada único" in text


def test_run_dispatches_to_the_pipeline(monkeypatch, tmp_path):
    calls = []
    monkeypatch.setattr(retrain, "use", lambda python, modules: None)
    monkeypatch.setattr(retrain, "RUNS", tmp_path)
    monkeypatch.setattr(retrain, "run_cycle", lambda *args: calls.append(args))
    retrain.main(["run", "--name", "c1", "--no-sync"])
    assert calls == [(tmp_path / "c1", None, False, None)]


def test_promote_requires_a_finished_cycle(monkeypatch, tmp_path):
    monkeypatch.setattr(retrain, "use", lambda python, modules: None)
    monkeypatch.setattr(retrain, "RUNS", tmp_path)
    with pytest.raises(SystemExit, match="comparison.json"):
        retrain.main(["promote", "--name", "c1", "--model-name", "m"])


def metrics(macro_f1, top1):
    return {"top1_accuracy": top1, "macro_f1": macro_f1, "top3_accuracy": 1.0, "coverage": 1.0, "accepted_accuracy": 1.0}


def test_recommendation_needs_field_evidence_and_bounded_tla_drop():
    production = {"test": metrics(0.80, 0.85), "field_test": metrics(0.5, 0.60)}
    good = recommendation({"test": metrics(0.79, 0.84), "field_test": metrics(0.5, 0.60)}, production, 0.02)
    assert good["recommended_for_promotion"] and good["reasons"] == []
    drop = recommendation({"test": metrics(0.77, 0.84), "field_test": metrics(0.6, 0.70)}, production, 0.02)
    assert not drop["recommended_for_promotion"] and "Macro-F1" in drop["reasons"][0]
    worse = recommendation({"test": metrics(0.81, 0.86), "field_test": metrics(0.6, 0.59)}, production, 0.02)
    assert not worse["recommended_for_promotion"] and "campo" in worse["reasons"][0]
    no_field = recommendation({"test": metrics(0.81, 0.86)}, {"test": production["test"]}, 0.02)
    assert not no_field["recommended_for_promotion"] and "Sem teste de campo" in no_field["reasons"][0]
    smoke = recommendation({"test": metrics(0.9, 0.9), "field_test": metrics(0.9, 0.9)}, production, 0.02, smoke=True)
    assert not smoke["recommended_for_promotion"]
