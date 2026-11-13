#!/usr/bin/env python3
"""Retreino do LeafCare: ponto de entrada único.

Fluxo: `setup` (uma vez) → `status` → `run --name <ciclo>` → revisar runs/<ciclo>/comparison.json
→ `promote` (só por decisão explícita). Detalhes de cada etapa: `python retrain.py <comando> --help`.

Credenciais só pelo ambiente: SUPABASE_URL e SUPABASE_SERVICE_ROLE_KEY (nunca em arquivos ou logs).
"""
from __future__ import annotations

import argparse
import importlib.util
import json
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
TRAIN_PYTHON = ROOT / ".cache/retrain-venv/bin/python"
EXPORT_PYTHON = ROOT / ".cache/retrain-export-venv/bin/python"
RUNS = ROOT / "runs"
ENVIRONMENTS = ((TRAIN_PYTHON, "requirements-train.txt", "auto"), (EXPORT_PYTHON, "requirements-export.txt", "cpu"))


def use(python: Path, modules: tuple[str, ...]) -> None:
    """Re-run this command with ``python`` (a venv from `setup`) unless it is already the interpreter."""
    venv = python.parent.parent
    if python.exists() and Path(sys.prefix).resolve() != venv.resolve():
        os.execv(str(python), [str(python), str(Path(__file__).resolve()), *sys.argv[1:]])
    missing = [m for m in modules if importlib.util.find_spec(m) is None]
    if missing:
        raise SystemExit(f"Faltam pacotes ({', '.join(missing)}). Rode `python retrain.py setup` antes.")


def name(value: str) -> str:
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{0,79}", value):
        raise argparse.ArgumentTypeError("use letras, números, '.', '_' ou '-' (ex.: 2026-12-retreino)")
    return value


def show(value) -> None:
    print(json.dumps(value, ensure_ascii=False, indent=2))


def cmd_setup(args) -> None:
    uv = shutil.which("uv")
    if not uv:
        raise SystemExit("Instale o uv (https://docs.astral.sh/uv/) e rode de novo.")
    for python, requirements, backend in ENVIRONMENTS:
        if not python.exists():
            subprocess.run([uv, "venv", "--python", "3.12", str(python.parent.parent)], check=True)
        subprocess.run([uv, "pip", "install", "--python", str(python), "--torch-backend", backend,
                        "-r", str(ROOT / requirements)], check=True)
    print("Ambientes prontos: .cache/retrain-venv (treino) e .cache/retrain-export-venv (TFLite).")


def cmd_status(args) -> None:
    use(TRAIN_PYTHON, ("yaml",))
    from datetime import datetime, timezone

    from leafcare.common import load_config
    from training import sync
    now = datetime.now(timezone.utc)
    rows = sync.Supabase(*sync.credentials()).pool()
    show({"server": sync.pool_status(rows, now, load_config()["expiring_soon_days"]),
          "local_mirror": sync.mirror_status(now=now)})


def cmd_sync(args) -> None:
    use(TRAIN_PYTHON, ("yaml", "PIL", "numpy"))
    from leafcare.common import load_config
    from training import sync
    client = sync.Supabase(*sync.credentials())
    show({"tla": sync.ensure_tla(client), **sync.sync(client, load_config())})


def cmd_tla_upload(args) -> None:
    use(TRAIN_PYTHON, ())
    from training import sync
    show(sync.upload_tla(sync.Supabase(*sync.credentials())))


def export_step(run: Path) -> dict:
    """TFLite conversion and replay need the export environment: inline there, otherwise a subprocess."""
    if importlib.util.find_spec("litert_torch") is not None:
        from training import export
        export.convert(run)
        return export.replay(run)
    if not EXPORT_PYTHON.exists():
        raise SystemExit("Ambiente de exportação ausente; rode `python retrain.py setup`.")
    subprocess.run([str(EXPORT_PYTHON), str(Path(__file__).resolve()), "_export", "--run-dir", str(run)], check=True)
    return json.loads((run / "comparison.json").read_text(encoding="utf-8"))


def run_cycle(run: Path, teacher: Path | None = None, do_sync: bool = True, device_name: str | None = None,
              teacher_spec: dict | None = None, epochs=None, teachers: Path | None = None, smoke: bool = False) -> dict:
    """sync → dataset.json → teacher → student → TFLite + parity → replay → comparison; finished steps are skipped."""
    import torch

    from leafcare.common import load_config, read_json, write_json
    from training import dataset as cycle
    from training import sync, train
    config = load_config()
    if do_sync:
        client = sync.Supabase(*sync.credentials())
        show({"tla": sync.ensure_tla(client), "sync": sync.sync(client, config)})
    sync.prune_expired()
    if not (run / "dataset.json").exists():
        built = cycle.build(config)
        run.mkdir(parents=True, exist_ok=True)
        draft = run / "dataset.json.tmp"
        write_json(draft, built)
        cycle.load_dataset(draft)  # the cycle only exists once every image it references is present
        os.replace(draft, run / "dataset.json")
        show({k: built[k] for k in ("split_sizes", "field_counts", "field_groups", "rejected", "warnings")})
    dataset, roots = cycle.load_dataset(run / "dataset.json")
    torch.set_num_threads(2)
    device = torch.device(device_name or ("cuda" if torch.cuda.is_available() else "cpu"))
    summary = run / "run_summary.json"
    if not summary.exists() or read_json(summary).get("status") != "completed":
        spec = teacher_spec or train.TEACHER
        if teacher is None:
            teacher = train.train_teacher(dataset, roots, device, spec, epochs, teachers or train.TEACHERS)
        train.train_student(run, teacher, spec, device, epochs, smoke)
    comparison = export_step(run)
    show({"run": str(run), "summary": read_json(summary), **{k: comparison[k] for k in ("recommended_for_promotion", "reasons")}})
    return comparison


def cmd_run(args) -> None:
    use(TRAIN_PYTHON, ("torch", "timm", "sklearn", "yaml"))
    if args.teacher and not args.teacher.is_file():
        raise SystemExit(f"Professor não encontrado: {args.teacher}")
    run_cycle(RUNS / args.name, args.teacher, not args.no_sync, args.device)


def cmd_promote(args) -> None:
    use(TRAIN_PYTHON, ("numpy", "PIL"))
    from training import promote
    run = RUNS / args.name
    if not (run / "comparison.json").exists():
        raise SystemExit(f"{run} não tem comparison.json; conclua `run --name {args.name}` antes.")
    metadata = promote.install(run, args.model_name, args.allow_unrecommended)
    show({"installed": True, "model_sha256": metadata["model_sha256"], "report": metadata["report"],
          "next": "Rode os testes Android e gere o APK antes de distribuir."})


def cmd_export(args) -> None:
    from training import export
    export.convert(args.run_dir)
    export.replay(args.run_dir)


def parser() -> argparse.ArgumentParser:
    main = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    commands = main.add_subparsers(dest="command", required=True, metavar="{setup,status,sync,run,promote,tla-upload}")

    setup = commands.add_parser("setup", help="Cria/atualiza os dois ambientes (Python 3.12, uv).",
                                description="Cria .cache/retrain-venv (treino; PyTorch com CUDA quando houver GPU) e "
                                            ".cache/retrain-export-venv (conversão TFLite, CPU) com as versões fixadas "
                                            "em requirements-train.txt e requirements-export.txt.")
    setup.set_defaults(func=cmd_setup)

    status = commands.add_parser("status", help="Contagens do pool no servidor e do espelho local; não baixa nada.",
                                 description="Mostra cópias por estado (ready, awaiting_review, excluded), prontas por "
                                             "classe, cópias v1.4 que vencem em breve e o conteúdo de data/field/.")
    status.set_defaults(func=cmd_status)

    sync = commands.add_parser("sync", help="Atualiza o espelho local data/field/ com as fotos prontas.",
                               description="Baixa fotos novas em estado ready, apaga as que saíram de ready (revogadas, "
                                           "excluídas, revisadas como incertas/rejeitadas ou vencidas) e atualiza "
                                           "rótulos revisados sem novo download, uma pasta por doença. Também baixa "
                                           "do bucket tla-dataset as imagens do TLA que faltarem em data/raw/.")
    sync.set_defaults(func=cmd_sync)

    run = commands.add_parser("run", help="Executa um ciclo completo (retoma etapas já concluídas).",
                              description="sync → runs/<ciclo>/dataset.json (TLA + fotos prontas) → professor DINOv2-B "
                                          "→ aluno MobileNetV4 Small destilado → TFLite com paridade → replay com o "
                                          "pré-processamento do app → comparison.json com a recomendação. Nada muda "
                                          "no app.")
    run.add_argument("--name", required=True, type=name, help="Nome do ciclo (pasta em runs/), ex.: 2026-12-retreino.")
    run.add_argument("--teacher", type=Path, help="Checkpoint safetensors de um professor DINOv2-B já treinado; "
                                                  "sem ele, o professor é treinado (ou reaproveitado do cache).")
    run.add_argument("--no-sync", action="store_true", help="Não consulta o servidor; usa o espelho local como está.")
    run.add_argument("--device", choices=["cpu", "cuda"], help="Padrão: cuda quando disponível.")
    run.set_defaults(func=cmd_run)

    promote = commands.add_parser("promote", help="Instala no app o candidato de um ciclo (decisão explícita).",
                                  description="Copia o TFLite do ciclo para artifacts/ e para os assets Android, grava "
                                              "os metadados e o relatório docs/model-reports/<model-name>/.")
    promote.add_argument("--name", required=True, type=name, help="Ciclo já executado com `run`.")
    promote.add_argument("--model-name", required=True, type=name, help="Pasta do relatório, ex.: mobilenetv4-2026-12.")
    promote.add_argument("--allow-unrecommended", action="store_true",
                         help="Promove mesmo sem recomendação automática; só com decisão registrada da equipe.")
    promote.set_defaults(func=cmd_promote)

    upload = commands.add_parser("tla-upload", help="Envia o TLA de data/raw/ para o bucket privado tla-dataset (uma vez).",
                                 description="Confere data/raw/ com o manifesto e envia cada imagem para tla-dataset, "
                                             "uma pasta por doença; imagens que já estão no bucket não são substituídas.")
    upload.set_defaults(func=cmd_tla_upload)

    internal = commands.add_parser("_export")  # invoked by `run` inside the export environment
    internal.add_argument("--run-dir", type=Path, required=True)
    internal.set_defaults(func=cmd_export)
    return main


def main(argv=None) -> None:
    args = parser().parse_args(argv)
    try:
        args.func(args)
    except (ValueError, FileNotFoundError) as error:
        raise SystemExit(f"Erro: {error}") from error


if __name__ == "__main__":
    main()
