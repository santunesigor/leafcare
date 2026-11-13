import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CONFIG_KEYS = {"seed", "field_split", "min_field_groups", "near_duplicate_hamming", "min_image_side",
               "expiring_soon_days", "max_tla_macro_f1_drop"}


def load_config(path=ROOT / "config.yaml"):
    import yaml
    config = yaml.safe_load(Path(path).read_text(encoding="utf-8"))
    if set(config) != CONFIG_KEYS:
        raise ValueError(f"config.yaml deve ter exatamente as chaves {sorted(CONFIG_KEYS)}.")
    ratios = config["field_split"]
    if list(ratios) != ["train", "validation", "field_test"] or any(v <= 0 for v in ratios.values()):
        raise ValueError("field_split precisa de proporções positivas para train, validation e field_test, nessa ordem.")
    if abs(sum(ratios.values()) - 1) > 1e-8:
        raise ValueError("As proporções de field_split devem somar 1.")
    return config


def write_json(path, value):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2, allow_nan=False) + "\n", encoding="utf-8")


def read_json(path):
    return json.loads(Path(path).read_text(encoding="utf-8"))


def sha256(path):
    h = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def text_sha256(path):
    # Git may check JSON out with CRLF on Windows; hash the canonical LF bytes.
    return hashlib.sha256(Path(path).read_bytes().replace(b"\r\n", b"\n")).hexdigest()


def classes_hash(classes):
    return hashlib.sha256("\n".join(classes).encode("utf-8")).hexdigest()
