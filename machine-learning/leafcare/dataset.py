import csv
import hashlib
from collections import Counter, defaultdict
from pathlib import Path

import numpy as np
from PIL import Image

from .common import location, write_json, read_json, sha256, classes_hash
from .preprocessing import decode_rgb


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


def audit_dataset(root):
    root = Path(root)
    if not root.is_dir():
        raise FileNotFoundError("Dataset ausente. Envie/extraia as imagens em data/raw/<classe>/.")
    classes = sorted(p.name for p in root.iterdir() if p.is_dir() and not p.name.startswith("."))
    if len(classes) < 3:
        raise ValueError("São necessárias pelo menos três pastas de classes para top 3.")
    rows, invalid, duplicates, conflicts = [], [], [], []
    seen = {}
    for label in classes:
        for path in sorted((root / label).rglob("*")):
            if not path.is_file() or path.name.startswith("."):
                continue
            relative = path.relative_to(root).as_posix()
            try:
                if path.is_symlink() or not path.resolve().is_relative_to(root.resolve()):
                    raise ValueError("Links simbólicos não são aceitos.")
                rgb = decode_rgb(path)
                pixel_hash = hashlib.sha256(str(rgb.shape).encode() + rgb.tobytes()).hexdigest()
                row = {"path": relative, "class_id": label, "sha256": sha256(path), "pixel_sha256": pixel_hash,
                       "width": rgb.shape[1], "height": rgb.shape[0], "dhash": difference_hash(rgb)}
                if pixel_hash in seen:
                    original = seen[pixel_hash]
                    item = {"path": relative, "original": original["path"]}
                    (duplicates if original["class_id"] == label else conflicts).append(item)
                else:
                    seen[pixel_hash] = row
                    rows.append(row)
            except (ValueError, OSError, Image.DecompressionBombError) as error:
                invalid.append({"path": relative, "reason": str(error)})
    report = {"status": "audited", "classes": classes, "valid_unique_images": len(rows),
              "counts": dict(Counter(r["class_id"] for r in rows)), "invalid_files": invalid,
              "exact_duplicates_removed": duplicates, "label_conflicts": conflicts,
              "root_files_ignored": sorted(p.name for p in root.iterdir() if p.is_file())}
    return rows, report


def build_groups(rows, groups_csv=None, distance=4):
    uf = UnionFind(len(rows))
    metadata = {}
    if groups_csv:
        with Path(groups_csv).open(encoding="utf-8-sig", newline="") as stream:
            for row in csv.DictReader(stream):
                if not row.get("path") or not row.get("group_id") or row["path"] in metadata:
                    raise ValueError("groups.csv requer path,group_id únicos e não vazios.")
                metadata[row["path"]] = row["group_id"]
        missing = [r["path"] for r in rows if r["path"] not in metadata]
        if missing:
            raise ValueError("groups.csv não cobre todas as imagens únicas: " + ", ".join(missing[:5]))
    known = {}
    near_pairs = []
    for i, row in enumerate(rows):
        group = metadata.get(row["path"], row["path"])
        if group in known:
            uf.union(i, known[group])
        known[group] = i
        for j in range(i):
            if (row["dhash"] ^ rows[j]["dhash"]).bit_count() <= distance:
                uf.union(i, j)
                near_pairs.append([rows[j]["path"], row["path"]])
    for i, row in enumerate(rows):
        row["group_id"] = f"g{uf.find(i):07d}"
    return near_pairs


def stratified_group_split(rows, classes, ratios, seed, minimum_groups=10):
    """Busca determinística de uma alocação por grupos, minimizando desvio por classe.

    Grupos podem conter várias classes; nunca são divididos. As proporções são
    aproximadas devido aos tamanhos dos grupos, e são reportadas por classe.
    """
    by_group = defaultdict(list)
    for row in rows:
        by_group[row["group_id"]].append(row)
    counts = {c: len({r["group_id"] for r in rows if r["class_id"] == c}) for c in classes}
    too_small = {c: n for c, n in counts.items() if n < minimum_groups}
    if too_small:
        raise ValueError(f"Grupos independentes insuficientes (mínimo operacional {minimum_groups}/classe, não garantia estatística): {too_small}")
    groups = sorted(by_group)
    vectors = np.array([[sum(r["class_id"] == c for r in by_group[g]) for c in classes] for g in groups])
    target = np.array(list(ratios.values()))[:, None] * vectors.sum(axis=0)
    rng = np.random.default_rng(seed)
    best = None
    for _ in range(512):
        assignment = np.full(len(groups), -1)
        current = np.zeros_like(target)
        order = rng.permutation(len(groups))
        for idx in order:
            costs = []
            for split in range(3):
                candidate = current.copy()
                candidate[split] += vectors[idx]
                costs.append(np.sum(((candidate - target) / np.maximum(target, 1)) ** 2))
            chosen = int(np.argmin(np.asarray(costs) + rng.uniform(0, 1e-9, 3)))
            assignment[idx] = chosen
            current[chosen] += vectors[idx]
        if (current == 0).any():
            continue
        cost = float(np.sum(((current - target) / np.maximum(target, 1)) ** 2))
        if best is None or cost < best[0]:
            best = cost, assignment.copy()
    if best is None:
        raise ValueError("Não foi possível formar três subconjuntos com todas as classes sem dividir grupos.")
    assigned = dict(zip(groups, best[1].tolist()))
    names = list(ratios)
    for row in rows:
        row["split"] = names[assigned[row["group_id"]]]
    return rows


def prepare(config, audit_only=False):
    output = location(config, "prepared_dir")
    output.mkdir(parents=True, exist_ok=True)
    if not audit_only and (output / "manifest.json").exists():
        raise ValueError("Já existe uma divisão. Use outra prepared_dir para preservar a reprodutibilidade.")
    rows, report = audit_dataset(location(config, "dataset_dir"))
    report["seed"] = config["seed"]
    write_json(output / "audit.json", report)
    if audit_only:
        return report
    try:
        if report["label_conflicts"]:
            raise ValueError("Mesmos pixels em classes diferentes. Corrija os rótulos; consulte audit.json.")
        if report["invalid_files"]:
            raise ValueError("Arquivos inválidos encontrados; remova/corrija os itens de audit.json.")
        groups = config.get("groups_csv")
        report["near_duplicate_pairs"] = build_groups(rows, config["_base"] / groups if groups else None, config["near_duplicate_hamming"])
        report["independent_groups_per_class"] = {
            c: len({r["group_id"] for r in rows if r["class_id"] == c}) for c in report["classes"]
        }
        report["group_metadata_provided"] = bool(groups)
        report["warning"] = "Hashes não reconhecem todas as fotos da mesma planta. Forneça grupos de origem e revise pares semelhantes."
        ratios = {k: config["split"][k] for k in ("train", "validation", "test")}
        stratified_group_split(rows, report["classes"], ratios, config["seed"], config["minimum_groups_per_class"])
        report["split_counts"] = {s: dict(Counter(r["class_id"] for r in rows if r["split"] == s)) for s in ratios}
        report["status"] = "prepared"
        write_json(output / "classes.json", report["classes"])
        write_json(output / "manifest.json", {"seed": config["seed"], "classes": report["classes"],
                   "classes_sha256": classes_hash(report["classes"]), "rows": rows})
    except ValueError as error:
        report.update(status="blocked", reason=str(error))
        raise
    finally:
        write_json(output / "audit.json", report)
    return report


def load_manifest(config, verify=True):
    path = location(config, "prepared_dir") / "manifest.json"
    if not path.exists():
        raise FileNotFoundError("Execute prepare_dataset.py com um dataset completo antes desta etapa.")
    manifest = read_json(path)
    classes = read_json(path.parent / "classes.json")
    if classes != manifest["classes"] or classes_hash(classes) != manifest["classes_sha256"]:
        raise ValueError("classes.json diverge do manifesto.")
    groups, hashes = {}, {}
    for row in manifest["rows"]:
        for mapping, key in [(groups, row["group_id"]), (hashes, row["pixel_sha256"])]:
            if key in mapping and mapping[key] != row["split"]:
                raise ValueError("Vazamento entre os subconjuntos.")
            mapping[key] = row["split"]
        path = location(config, "dataset_dir") / row["path"]
        if not path.resolve().is_relative_to(location(config, "dataset_dir")):
            raise ValueError("Caminho fora do dataset.")
        if verify and sha256(path) != row["sha256"]:
            raise ValueError("Imagem modificada após a divisão: " + row["path"])
    return manifest
