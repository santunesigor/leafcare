"""One retraining cycle's dataset: TLA with its fixed split plus the field photos of the mirror.

TLA rows keep the historical train/validation/test split (data/prepared/manifest.json),
so every model is compared on the same TLA test. Field photos are split per person
group, deterministically: a group always lands in the same split, so adding photos
in later cycles never moves anyone. Near-duplicate field photos are merged into one
group; field photos that repeat a TLA image are dropped. The held-out field part is
``field_test``. Below ``min_field_groups`` groups everything goes to train.

``dataset.json`` only references images (data/raw for TLA, data/field for photos).
"""
from __future__ import annotations

import hashlib
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path

import numpy as np
from PIL import Image

from leafcare.common import ROOT, classes_hash, read_json, sha256, text_sha256
from training import sync

SCHEMA = "leafcare-cycle-dataset-v2"
BASE_MANIFEST = ROOT / "data/prepared/manifest.json"
TLA_ROOT = ROOT / "data/raw"
SPLITS = ("train", "validation", "test", "field_test")


class UnionFind:
    def __init__(self, n):
        self.parent = list(range(n))

    def find(self, x):
        while x != self.parent[x]:
            self.parent[x] = self.parent[self.parent[x]]
            x = self.parent[x]
        return x

    def union(self, a, b):
        a, b = self.find(a), self.find(b)
        self.parent[max(a, b)] = min(a, b)


def difference_hash(rgb):
    tiny = np.asarray(Image.fromarray(rgb).convert("L").resize((9, 8), Image.Resampling.BILINEAR))
    return int.from_bytes(np.packbits(tiny[:, 1:] > tiny[:, :-1]).tobytes(), "big")


def field_split(group_id: str, seed: int, ratios: dict[str, float]) -> str:
    """Deterministic per group: adding photos never moves an existing group."""
    value = int(hashlib.sha256(f"{seed}:{group_id}".encode()).hexdigest()[:12], 16) / 16**12
    cumulative = 0.0
    for split, ratio in ratios.items():
        cumulative += ratio
        if value < cumulative:
            return split
    return list(ratios)[-1]


def relative(path: Path) -> str:
    return str(path.relative_to(ROOT)) if path.is_relative_to(ROOT) else str(path)


def build(config: dict, base_manifest: Path = BASE_MANIFEST, mirror: Path = sync.MIRROR,
          tla_root: Path = TLA_ROOT, now: datetime | None = None) -> dict:
    now = now or datetime.now(timezone.utc)
    base = read_json(base_manifest)
    classes = base["classes"]
    distance, seed = config["near_duplicate_hamming"], config["seed"]
    rows = [{**row, "source": "tla"} for row in base["rows"]]
    tla_pixels = {row["pixel_sha256"] for row in rows}
    tla_hashes = [row["dhash"] for row in rows]
    photos = sync.load_mirror(mirror)
    field, rejected = [], Counter()
    for key, photo in sorted(photos["photos"].items()):
        path = mirror / photo["image"]
        if sync.expired(photo["expires_at"], now):
            rejected["expired"] += 1
        elif photo["class_id"] not in classes:
            rejected["unknown_class"] += 1
        elif not path.is_file() or sha256(path) != photo["sha256"]:
            rejected["missing_or_changed"] += 1
        elif photo["pixel_sha256"] in tla_pixels or any((photo["dhash"] ^ h).bit_count() <= distance for h in tla_hashes):
            rejected["near_duplicate_of_tla"] += 1
        else:
            field.append({"path": photo["image"], "source": "field", "class_id": photo["class_id"],
                          "review_status": photo["review_status"], "sha256": photo["sha256"],
                          "pixel_sha256": photo["pixel_sha256"], "width": photo["width"], "height": photo["height"],
                          "dhash": photo["dhash"], "group_id": photo["group"]})

    # Same person or near-duplicate photos share a group; the group is named by its
    # smallest person key so the name (and split) only changes when groups merge.
    union = UnionFind(len(field))
    first: dict[str, int] = {}
    for i, row in enumerate(field):
        union.union(i, first.setdefault(row["group_id"], i))
        for j in range(i):
            if (row["dhash"] ^ field[j]["dhash"]).bit_count() <= distance:
                union.union(i, j)
    names: dict[int, str] = {}
    for i, row in enumerate(field):
        root = union.find(i)
        names[root] = min(names.get(root, row["group_id"]), row["group_id"])
    groups = sorted(set(names.values()))
    holdout = len(groups) >= config["min_field_groups"]
    for i, row in enumerate(field):
        row["group_id"] = names[union.find(i)]
        row["split"] = field_split(row["group_id"], seed, config["field_split"]) if holdout else "train"

    all_rows = rows + field
    counts = {split: dict(sorted(Counter(r["class_id"] for r in all_rows if r["split"] == split).items())) for split in SPLITS}
    warnings = []
    if not field:
        warnings.append("Nenhuma foto de campo utilizável: o ciclo treina só com o TLA.")
    elif not holdout:
        warnings.append(f"Só {len(groups)} grupo(s) de pessoas (mínimo {config['min_field_groups']} para separar o teste "
                        "de campo): todas as fotos de campo foram para o treino e não há avaliação em fotos reais.")
    missing = [c for c in classes if field and not any(r["class_id"] == c for r in field)]
    if missing:
        warnings.append("Classes sem foto de campo: " + ", ".join(missing))
    return {
        "schema": SCHEMA, "created_at": now.isoformat(), "seed": seed,
        "classes": classes, "classes_sha256": classes_hash(classes),
        "base_manifest_sha256": text_sha256(base_manifest),
        "roots": {"tla": relative(tla_root), "field": relative(mirror)},
        "mirror_synced_at": photos["synced_at"],
        "field_ratios": config["field_split"] if holdout else {"train": 1.0}, "field_groups": len(groups),
        "counts": counts, "split_sizes": {s: sum(c.values()) for s, c in counts.items()},
        "field_counts": {s: sum(1 for r in field if r["split"] == s) for s in SPLITS},
        "rejected": dict(sorted(rejected.items())), "warnings": warnings, "rows": all_rows,
    }


def resolve(value: str) -> Path:
    path = Path(value)
    return path if path.is_absolute() else ROOT / path


def load_dataset(path: Path, now: datetime | None = None):
    """Check a cycle's dataset still matches the disk and the mirror before any use."""
    now = now or datetime.now(timezone.utc)
    dataset = read_json(path)
    if dataset.get("schema") != SCHEMA:
        raise ValueError(f"{path} não foi gerado por esta versão do pipeline; monte um novo ciclo.")
    if dataset["classes"] != read_json(ROOT / "artifacts/classes.json"):
        raise ValueError("A ordem das classes difere do modelo do app; o contrato de 16 classes não muda no retreino.")
    roots = {name: resolve(value) for name, value in dataset["roots"].items()}
    mirror = sync.load_mirror(roots["field"])["photos"]
    changed = [r["path"] for r in dataset["rows"] if r["source"] == "field" and (
        (photo := mirror.get(Path(r["path"]).stem)) is None or photo["class_id"] != r["class_id"]
        or sync.expired(photo["expires_at"], now))]
    if changed:
        raise ValueError(f"{len(changed)} foto(s) de campo saíram do espelho ou mudaram de rótulo desde que o ciclo foi "
                         "montado (revogação, exclusão, revisão ou validade). Monte um novo ciclo com outro --name.")
    missing = [r["path"] for r in dataset["rows"]
               if not (roots[r["source"]] / r["path"]).is_file() or sha256(roots[r["source"]] / r["path"]) != r["sha256"]]
    if missing:
        raise FileNotFoundError(f"{len(missing)} imagens ausentes ou alteradas, por exemplo {missing[:3]}. "
                                "O TLA fica em data/raw (import_tla.py) e as fotos de campo em data/field (sync).")
    return dataset, roots


def split_rows(dataset: dict, split: str) -> list[dict]:
    return [row for row in dataset["rows"] if row["split"] == split]
