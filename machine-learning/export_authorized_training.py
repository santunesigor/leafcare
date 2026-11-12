"""Export the training copies that may enter a dataset to a private local folder.

Eligibility is decided by the database function ``training_pool()`` (Terms and
Privacy v1.4): a copy younger than 90 days, of an active and conclusive analysis
made after the owner accepted v1.4, with current consent and authorization, an
account not being deleted and a human review that confirmed or corrected the
class. Only rows in state ``ready`` are downloaded.

Each export:
- removes the previous export first, so revocations and deletions are reconciled;
- re-encodes every image (no EXIF, no account identifiers in names or manifest);
- groups photos by owner with a random per-export key that is never stored, so
  splits keep each person's photos together without linking exports;
- expires 30 days after it was created (``--cleanup-only`` removes it).

Required environment: SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY. The service key
belongs only to this trusted pipeline: never in the APK, in versioned files or logs.
"""
from __future__ import annotations

import hashlib
import hmac
import io
import json
import os
import secrets
import shutil
import urllib.error
import urllib.parse
import urllib.request
from collections import Counter
from datetime import datetime, timedelta, timezone
from pathlib import Path

# Image libraries load lazily: the daily cleanup timer runs with the system
# Python and must work with the standard library alone.

OUTPUT = Path(__file__).parent / "data" / "authorized_training"
MIN_SIDE = 128
TRAINING_EXPORT_ENABLED = True
EXPORT_RETENTION = timedelta(days=30)
MANIFEST_SCHEMA = 2


def cleanup_expired_export(output: Path = OUTPUT, now: datetime | None = None) -> bool:
    """Remove a local dataset export once it has reached its 30-day limit."""
    if not output.exists():
        return False
    now = now or datetime.now(timezone.utc)
    manifest = output / "manifest.json"
    exported_at = None
    if manifest.is_file():
        try:
            exported_at = json.loads(manifest.read_text()).get("exported_at")
        except (OSError, json.JSONDecodeError):
            exported_at = None
    try:
        created = datetime.fromisoformat(exported_at) if exported_at else datetime.fromtimestamp(output.stat().st_mtime, timezone.utc)
    except (ValueError, OSError):
        created = datetime.fromtimestamp(output.stat().st_mtime, timezone.utc)
    if created.tzinfo is None:
        created = created.replace(tzinfo=timezone.utc)
    if now - created.astimezone(timezone.utc) < EXPORT_RETENTION:
        return False
    shutil.rmtree(output)
    return True


def valid_image(data: bytes, scratch: Path):
    """Decode like training and the app (EXIF orientation, sRGB, alpha on white)."""
    from PIL import Image
    from leafcare.preprocessing import decode_rgb
    if not data:
        raise ValueError("arquivo vazio")
    scratch.write_bytes(data)
    try:
        rgb = decode_rgb(scratch)
    finally:
        scratch.unlink(missing_ok=True)
    if min(rgb.shape[:2]) < MIN_SIDE:
        raise ValueError("resolução abaixo de 128 px")
    return Image.fromarray(rgb)


def request(url: str, key: str, body: dict | None = None) -> bytes:
    headers = {"apikey": key, "Authorization": f"Bearer {key}"}
    data = None
    if body is not None:
        headers["Content-Type"] = "application/json"
        data = json.dumps(body).encode()
    req = urllib.request.Request(url, data=data, headers=headers, method="POST" if data is not None else "GET")
    with urllib.request.urlopen(req, timeout=60) as response:
        return response.read()


def fetch_pool(base_url: str, key: str) -> list[dict]:
    """All current training copies with their state; nothing is downloaded."""
    return json.loads(request(f"{base_url.rstrip('/')}/rest/v1/rpc/training_pool", key, {}))


def pool_status(rows: list[dict], now: datetime | None = None) -> dict:
    """Counts by state, ready photos by reviewed class and copies about to expire."""
    now = now or datetime.now(timezone.utc)
    soon = now + timedelta(days=14)

    def expires(row):
        return datetime.fromisoformat(row["expires_at"]).astimezone(timezone.utc)
    states = Counter(row["state"] for row in rows)
    return {
        "copies": sum(n for state, n in states.items() if state != "ineligible"),
        "ready": states["ready"],
        "awaiting_review": states["awaiting_review"],
        "excluded": states["excluded"],
        "ineligible": states["ineligible"],
        "awaiting_review_expiring_14_days": sum(1 for r in rows if r["state"] == "awaiting_review" and expires(r) < soon),
        "ready_by_class": dict(sorted(Counter(r["label_class_id"] for r in rows if r["state"] == "ready").items())),
    }


def group_key(salt: bytes, user_id: str) -> str:
    """Same owner, same group inside one export; unlinkable across exports."""
    return "f" + hmac.new(salt, user_id.encode(), hashlib.sha256).hexdigest()[:15]


def export(base_url: str, key: str, output: Path = OUTPUT, now: datetime | None = None) -> dict:
    cleanup_expired_export(output, now)
    if output.exists():
        shutil.rmtree(output)
    if not TRAINING_EXPORT_ENABLED:
        raise RuntimeError("Exportação de treinamento desativada por configuração.")
    from PIL import Image
    from leafcare.dataset import difference_hash
    from leafcare.preprocessing import decode_rgb
    now = now or datetime.now(timezone.utc)
    rows = fetch_pool(base_url, key)
    status = pool_status(rows, now)
    salt = secrets.token_bytes(32)
    output.mkdir(parents=True)
    seen: set[str] = set()
    images = []
    rejected = Counter()
    try:
        for row in rows:
            if row.get("state") != "ready":
                continue
            if row.get("review_status") not in ("confirmed", "corrected") or not row.get("label_class_id"):
                rejected["review"] += 1
                continue
            encoded = urllib.parse.quote(row["object_path"], safe="/")
            try:
                data = request(f"{base_url.rstrip('/')}/storage/v1/object/authenticated/training-photos/{encoded}", key)
                image = valid_image(data, output / ".download")
            except (ValueError, OSError, urllib.error.URLError, Image.DecompressionBombError):
                rejected["download_or_quality"] += 1
                continue
            buffer = io.BytesIO()
            image.save(buffer, format="JPEG", quality=95, optimize=True)
            content = buffer.getvalue()
            digest = hashlib.sha256(content).hexdigest()
            # Re-encoding is deterministic: the same pixels give the same bytes.
            if digest in seen:
                rejected["duplicate"] += 1
                continue
            seen.add(digest)
            name = f"{digest[:20]}.jpg"
            (output / name).write_bytes(content)
            rgb = decode_rgb(output / name)
            pixel_hash = hashlib.sha256(str(rgb.shape).encode() + rgb.tobytes()).hexdigest()
            images.append({
                "image": name, "class_id": row["label_class_id"], "review_status": row["review_status"],
                "predicted_class_id": row["predicted_class_id"], "predicted_confidence": row["confidence"],
                "model_sha256": row["model_sha256"], "group_id": group_key(salt, row["user_id"]),
                "sha256": digest, "pixel_sha256": pixel_hash,
                "width": int(rgb.shape[1]), "height": int(rgb.shape[0]), "dhash": difference_hash(rgb),
            })
    except BaseException:
        shutil.rmtree(output, ignore_errors=True)
        raise
    images.sort(key=lambda item: item["image"])
    manifest = {
        "schema_version": MANIFEST_SCHEMA,
        "exported_at": now.isoformat(),
        "delete_after": (now + EXPORT_RETENTION).isoformat(),
        "source": "supabase training_pool() state=ready",
        "pool": status,
        "images": images,
        "rejected": dict(rejected),
        "privacy": "Sem e-mail, IDs de conta ou de análise; EXIF removido; group_id muda a cada exportação.",
    }
    (output / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2))
    return {"exported": len(images), "rejected": sum(rejected.values()), "pool": status}


def credentials() -> tuple[str, str]:
    url, secret = os.environ.get("SUPABASE_URL"), os.environ.get("SUPABASE_SERVICE_ROLE_KEY")
    if not url or not secret:
        raise SystemExit("Configure SUPABASE_URL e SUPABASE_SERVICE_ROLE_KEY no ambiente.")
    return url, secret


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--cleanup-only", action="store_true", help="Só apaga uma exportação com mais de 30 dias.")
    parser.add_argument("--status", action="store_true", help="Mostra o estado das cópias no servidor sem baixar fotos.")
    parser.add_argument("--output", type=Path, default=OUTPUT)
    args = parser.parse_args()
    if args.cleanup_only:
        print("export_expired" if cleanup_expired_export(args.output) else "no_expired_export")
        raise SystemExit(0)
    cleanup_expired_export(args.output)
    if not TRAINING_EXPORT_ENABLED:
        if args.output.exists():
            shutil.rmtree(args.output)
        raise SystemExit("Exportação de treinamento desativada por configuração.")
    url, secret = credentials()
    if args.status:
        print(json.dumps(pool_status(fetch_pool(url, secret)), ensure_ascii=False, indent=2))
        raise SystemExit(0)
    print(json.dumps(export(url, secret, args.output), ensure_ascii=False, indent=2))
