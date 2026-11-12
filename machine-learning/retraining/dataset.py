"""Assemble one retraining cycle: the TLA dataset with its fixed split plus field photos.

TLA rows keep their historical train/validation/test split, so results stay
comparable with every previous model. Field photos (from
``export_authorized_training.py``) are split by owner group: all photos of the
same person land in the same split, and near-duplicates are merged into one
group. Their held-out part is ``field_test``, the first evaluation on real app
photos. Field photos that repeat a TLA image are dropped.

The dataset file only references the exported images; it stops being usable when
the export expires (30 days), as the Terms require.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path

from leafcare.common import read_json, sha256, write_json
from leafcare.dataset import UnionFind

ROOT = Path(__file__).resolve().parents[1]
SCHEMA = "leafcare-retraining-dataset-v1"
SPLITS = ("train", "validation", "test", "field_test")
FIELD_RATIOS = {"train": 0.70, "validation": 0.15, "field_test": 0.15}
MIN_FIELD_GROUPS = 5
NEAR_DUPLICATE_BITS = 4


def field_split(group_id: str, seed: int, ratios: dict[str, float] = FIELD_RATIOS) -> str:
    """Deterministic per group: adding photos never moves an existing group."""
    value = int(hashlib.sha256(f"{seed}:{group_id}".encode()).hexdigest()[:12], 16) / 16**12
    cumulative = 0.0
    for split, ratio in ratios.items():
        cumulative += ratio
        if value < cumulative:
            return split
    return list(ratios)[-1]


def export_is_current(export: dict, now: datetime) -> bool:
    deadline = datetime.fromisoformat(export["delete_after"])
    return now < deadline.astimezone(timezone.utc)


def build(base_manifest: Path, export_dir: Path, seed: int = 42, now: datetime | None = None,
          min_field_groups: int = MIN_FIELD_GROUPS, distance: int = NEAR_DUPLICATE_BITS) -> dict:
    now = now or datetime.now(timezone.utc)
    base = read_json(base_manifest)
    classes = base["classes"]
    export = read_json(export_dir / "manifest.json")
    if export.get("schema_version") != 2:
        raise ValueError("Exportação em formato antigo; rode export_authorized_training.py novamente.")
    if not export_is_current(export, now):
        raise ValueError("Exportação vencida (30 dias). Rode a exportação de novo antes de treinar.")

    rows = [{**row, "source": "tla"} for row in base["rows"]]
    tla_pixels = {row["pixel_sha256"] for row in rows}
    tla_hashes = [row["dhash"] for row in rows]
    field, rejected = [], Counter()
    for image in export["images"]:
        if image["class_id"] not in classes:
            rejected["unknown_class"] += 1
            continue
        path = export_dir / image["image"]
        if not path.is_file() or sha256(path) != image["sha256"]:
            rejected["missing_or_changed"] += 1
            continue
        if image["pixel_sha256"] in tla_pixels or any((image["dhash"] ^ h).bit_count() <= distance for h in tla_hashes):
            rejected["near_duplicate_of_tla"] += 1
            continue
        field.append({
            "path": image["image"], "source": "field", "class_id": image["class_id"],
            "sha256": image["sha256"], "pixel_sha256": image["pixel_sha256"],
            "width": image["width"], "height": image["height"], "dhash": image["dhash"],
            "group_id": image["group_id"], "review_status": image["review_status"],
            "predicted_class_id": image["predicted_class_id"],
        })

    # Near-duplicates of each other share a split even across owners.
    union = UnionFind(len(field))
    first_of_group: dict[str, int] = {}
    for i, row in enumerate(field):
        if row["group_id"] in first_of_group:
            union.union(i, first_of_group[row["group_id"]])
        first_of_group.setdefault(row["group_id"], i)
        for j in range(i):
            if (row["dhash"] ^ field[j]["dhash"]).bit_count() <= distance:
                union.union(i, j)
    for i, row in enumerate(field):
        row["group_id"] = field[union.find(i)]["group_id"]
    groups = sorted({row["group_id"] for row in field})
    holdout = len(groups) >= min_field_groups
    for row in field:
        row["split"] = field_split(row["group_id"], seed) if holdout else "train"

    all_rows = rows + field
    counts = {split: dict(sorted(Counter(r["class_id"] for r in all_rows if r["split"] == split).items())) for split in SPLITS}
    warnings = []
    if not field:
        warnings.append("Nenhuma foto de campo utilizável: o ciclo repetiria o treino só com o TLA.")
    elif not holdout:
        warnings.append(f"Só {len(groups)} grupo(s) de campo (mínimo {min_field_groups} para separar teste de campo): "
                        "todas as fotos de campo foram para o treino e não há avaliação em fotos reais.")
    missing = [c for c in classes if not any(r["class_id"] == c for r in field)]
    if field and missing:
        warnings.append("Classes sem foto de campo: " + ", ".join(missing))
    field_test = [r for r in field if r["split"] == "field_test"]
    production = None
    if field_test:
        correct = sum(r["predicted_class_id"] == r["class_id"] for r in field_test)
        production = {"n_images": len(field_test), "top1_correct": correct, "top1_accuracy": correct / len(field_test),
                      "note": "Previsão registrada pelo modelo em uso no momento da análise, contra o rótulo revisado."}
    return {
        "schema": SCHEMA, "created_at": now.isoformat(), "seed": seed,
        "classes": classes, "classes_sha256": base["classes_sha256"],
        "base_manifest": str(base_manifest.relative_to(ROOT)) if base_manifest.is_relative_to(ROOT) else str(base_manifest),
        "base_manifest_sha256": hashlib.sha256(base_manifest.read_bytes().replace(b"\r\n", b"\n")).hexdigest(),
        "roots": {"tla": "data/raw", "field": str(export_dir.relative_to(ROOT)) if export_dir.is_relative_to(ROOT) else str(export_dir)},
        "field_export": {"exported_at": export["exported_at"], "delete_after": export["delete_after"], "images": len(export["images"])},
        "field_ratios": FIELD_RATIOS if holdout else {"train": 1.0}, "field_groups": len(groups),
        "counts": counts, "split_sizes": {s: sum(c.values()) for s, c in counts.items()},
        "field_counts": {s: sum(1 for r in field if r["split"] == s) for s in SPLITS},
        "production_model_on_field_test": production,
        "rejected": dict(rejected), "warnings": warnings, "rows": all_rows,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--run", required=True, help="Nome do ciclo, por exemplo 2026-12-retreino.")
    parser.add_argument("--base", type=Path, default=ROOT / "data/prepared/manifest.json")
    parser.add_argument("--export", type=Path, default=ROOT / "data/authorized_training")
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()
    dataset = build(args.base.resolve(), args.export.resolve(), args.seed)
    out = ROOT / "runs" / args.run
    if (out / "dataset.json").exists():
        raise SystemExit(f"{out}/dataset.json já existe; use outro --run para não sobrescrever um ciclo.")
    out.mkdir(parents=True, exist_ok=True)
    write_json(out / "dataset.json", dataset)
    print(json.dumps({k: dataset[k] for k in ("split_sizes", "field_counts", "field_groups", "rejected", "warnings",
                                              "production_model_on_field_test")}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
