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
from pathlib import Path
from PIL import Image, ImageOps

OUTPUT = Path(__file__).parent / "data" / "authorized_training"
MIN_SIDE = 128
CURRENT_TERMS_VERSION = "1.0"
TRAINING_EXPORT_ENABLED = False


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
    if output.exists():
        shutil.rmtree(output)
    if not TRAINING_EXPORT_ENABLED:
        raise RuntimeError("Exportação de treinamento desativada até a publicação dos termos, política de privacidade e prazo de retenção.")
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
    (output / "manifest.json").write_text(json.dumps({"images": manifest, "rejected": rejected}, ensure_ascii=False, indent=2))
    return {"exported": len(manifest), "rejected": sum(rejected.values())}


if __name__ == "__main__":
    if not TRAINING_EXPORT_ENABLED:
        if OUTPUT.exists():
            shutil.rmtree(OUTPUT)
        raise SystemExit("Exportação desativada até a publicação dos termos, política de privacidade e prazo de retenção.")
    url, secret = os.environ.get("SUPABASE_URL"), os.environ.get("SUPABASE_SERVICE_ROLE_KEY")
    if not url or not secret:
        raise SystemExit("Configure SUPABASE_URL e SUPABASE_SERVICE_ROLE_KEY no ambiente.")
    print(json.dumps(export(url, secret)))
