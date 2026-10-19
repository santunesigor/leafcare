"""Export the current, reviewed and explicitly authorized photos to a private local dataset.

Required environment: SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY. The service key
must never be shipped in the APK or written to logs. Output is deleted and rebuilt
on each invocation so revocations are reconciled before training.
"""
from __future__ import annotations

import hashlib
import json
import os
import shutil
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone, timedelta
from pathlib import Path
from PIL import Image, ImageOps

OUTPUT = Path(__file__).parent / "data" / "authorized_training"
MIN_SIDE = 128
CURRENT_TERMS_VERSION = "1.1"
TRAINING_EXPORT_ENABLED = True
EXPORT_RETENTION = timedelta(days=30)


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


def valid_image(data: bytes) -> Image.Image:
    if not data:
        raise ValueError("arquivo vazio")
    import io
    with Image.open(io.BytesIO(data)) as source:
        source.load()
        image = ImageOps.exif_transpose(source).convert("RGB")
    if min(image.size) < MIN_SIDE:
        raise ValueError("resolução abaixo de 128 px")
    return image


def request(url: str, key: str) -> bytes:
    req = urllib.request.Request(url, headers={"apikey": key, "Authorization": f"Bearer {key}"})
    with urllib.request.urlopen(req, timeout=30) as response:
        return response.read()


def export(base_url: str, key: str, output: Path = OUTPUT) -> dict:
    cleanup_expired_export(output)
    if output.exists():
        shutil.rmtree(output)
    if not TRAINING_EXPORT_ENABLED:
        raise RuntimeError("Exportação de treinamento desativada por configuração.")
    query = urllib.parse.urlencode({
        "select": "analysis_id,terms_version,training_consents!inner(granted,terms_version),analyses!inner(id,user_id,deleted_at,photo_reviews!inner(status,class_id))",
        "authorized": "eq.true", "training_consents.granted": "eq.true",
        "analyses.deleted_at": "is.null", "analyses.photo_reviews.status": "in.(confirmed,corrected)",
    }, safe="(),!*")
    rows = json.loads(request(f"{base_url.rstrip('/')}/rest/v1/training_photo_authorizations?{query}", key))
    output.mkdir(parents=True)
    seen: set[str] = set()
    manifest = []
    rejected = {"consent_version": 0, "review": 0, "duplicate": 0, "download_or_quality": 0}
    for row in rows:
        consent = row.get("training_consents") or {}
        analysis = row.get("analyses") or {}
        review = analysis.get("photo_reviews") or {}
        if isinstance(consent, list): consent = consent[0] if consent else {}
        if isinstance(analysis, list): analysis = analysis[0] if analysis else {}
        if isinstance(review, list): review = review[0] if review else {}
        if (not consent.get("granted") or consent.get("terms_version") != row.get("terms_version")
                or consent.get("terms_version") != CURRENT_TERMS_VERSION):
            rejected["consent_version"] += 1; continue
        if review.get("status") not in ("confirmed", "corrected") or not review.get("class_id"):
            rejected["review"] += 1; continue
        object_path = f"{analysis['user_id']}/{row['analysis_id']}.jpg"
        encoded_path = urllib.parse.quote(object_path, safe="/")
        try:
            data = request(f"{base_url.rstrip('/')}/storage/v1/object/authenticated/training-photos/{encoded_path}", key)
            digest = hashlib.sha256(data).hexdigest()
            if digest in seen:
                rejected["duplicate"] += 1; continue
            image = valid_image(data)
            anonymous_id = digest[:20]
            image.save(output / f"{anonymous_id}.jpg", format="JPEG", quality=95, optimize=True)
            seen.add(digest)
            manifest.append({"image": f"{anonymous_id}.jpg", "class_id": review["class_id"], "sha256": digest})
        except (ValueError, OSError, urllib.error.URLError, KeyError):
            rejected["download_or_quality"] += 1
    (output / "manifest.json").write_text(json.dumps({
        "exported_at": datetime.now(timezone.utc).isoformat(),
        "images": manifest,
        "rejected": rejected,
    }, ensure_ascii=False, indent=2))
    return {"exported": len(manifest), "rejected": sum(rejected.values())}


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument("--cleanup-only", action="store_true")
    args = parser.parse_args()
    if args.cleanup_only:
        print("export_expired" if cleanup_expired_export() else "no_expired_export")
        raise SystemExit(0)
    cleanup_expired_export()
    if not TRAINING_EXPORT_ENABLED:
        if OUTPUT.exists():
            shutil.rmtree(OUTPUT)
        raise SystemExit("Exportação de treinamento desativada por configuração.")
    url, secret = os.environ.get("SUPABASE_URL"), os.environ.get("SUPABASE_SERVICE_ROLE_KEY")
    if not url or not secret:
        raise SystemExit("Configure SUPABASE_URL e SUPABASE_SERVICE_ROLE_KEY no ambiente.")
    print(json.dumps(export(url, secret)))
