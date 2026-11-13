"""Local mirror (data/field/) of the app photos that are ready for training.

The database function ``training_pool()`` decides eligibility; only rows in state
``ready`` (human review confirmed or corrected the class) are mirrored. Every sync
reconciles the mirror with the server: new ready photos are downloaded, photos
that left ``ready`` (revoked, deleted, relabeled as uncertain/rejected, expired)
are deleted, and relabels only update the label.

The mirror is organised like data/raw: one folder per disease
(``data/field/<class_id>/<key>.jpg``); a relabel moves the file to its new folder.

The TLA base set lives in the private bucket ``tla-dataset`` with the same layout
as data/raw; ``ensure_tla`` restores any missing or altered image, checked against
the sha256 of the frozen manifest, and ``upload_tla`` seeds the bucket once.

Privacy: images are decoded like the app and re-encoded (no EXIF); file names and
the manifest carry no account or analysis ids, only HMAC-SHA256 keys derived with
a local secret (``data/field/.secret``, mode 600, never versioned). The keys are
stable, so a person keeps the same group (and split) across cycles.

Credentials come only from SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY and are
never written or printed.
"""
from __future__ import annotations

import hashlib
import hmac
import io
import json
import os
import secrets
import urllib.error
import urllib.parse
import urllib.request
from collections import Counter
from datetime import datetime, timedelta, timezone
from pathlib import Path

from leafcare.common import ROOT

MIRROR = ROOT / "data/field"
TLA_MANIFEST = ROOT / "data/prepared/manifest.json"
TLA_ROOT = ROOT / "data/raw"
SCHEMA = "leafcare-field-mirror-v1"
BUCKET = "training-photos"
TLA_BUCKET = "tla-dataset"
REVIEWED = ("confirmed", "corrected")


class Supabase:
    """Minimal service-role client: the key only travels in request headers."""

    def __init__(self, url: str, key: str):
        self.url, self._key = url.rstrip("/"), key

    def _request(self, path: str, body: dict | None = None, data: bytes | None = None,
                 extra: dict | None = None) -> bytes:
        headers = {"apikey": self._key, "Authorization": f"Bearer {self._key}", **(extra or {})}
        if body is not None:
            headers["Content-Type"] = "application/json"
            data = json.dumps(body).encode()
        request = urllib.request.Request(self.url + path, data=data, headers=headers,
                                         method="POST" if data is not None else "GET")
        with urllib.request.urlopen(request, timeout=60) as response:
            return response.read()

    def pool(self) -> list[dict]:
        # PostgREST caps rows per response (max-rows); page until an empty page.
        rows: list[dict] = []
        while True:
            page = json.loads(self._request(f"/rest/v1/rpc/training_pool?order=analysis_id&offset={len(rows)}", {}))
            if not page:
                return rows
            rows.extend(page)

    def object(self, bucket: str, path: str) -> bytes:
        return self._request(f"/storage/v1/object/authenticated/{bucket}/{urllib.parse.quote(path, safe='/')}")

    def photo(self, object_path: str) -> bytes:
        return self.object(BUCKET, object_path)

    def upload(self, bucket: str, path: str, data: bytes) -> bool:
        """Create an object; False when it already exists (never overwritten)."""
        try:
            self._request(f"/storage/v1/object/{bucket}/{urllib.parse.quote(path, safe='/')}", data=data,
                          extra={"Content-Type": "image/jpeg" if path.lower().endswith((".jpg", ".jpeg")) else "image/png",
                                 "x-upsert": "false"})
            return True
        except urllib.error.HTTPError as error:
            if error.code in (400, 409) and b"exist" in error.read().lower():
                return False
            raise


def credentials() -> tuple[str, str]:
    url, key = os.environ.get("SUPABASE_URL"), os.environ.get("SUPABASE_SERVICE_ROLE_KEY")
    if not url or not key:
        raise SystemExit("Configure SUPABASE_URL e SUPABASE_SERVICE_ROLE_KEY no ambiente (nunca em arquivos versionados).")
    return url, key


def parse_time(value: str | None) -> datetime | None:
    return datetime.fromisoformat(value).astimezone(timezone.utc) if value else None


def expired(expires_at: str | None, now: datetime) -> bool:
    """Copies under Terms v1.4 expire 90 days after upload; v1.5 copies have no date."""
    deadline = parse_time(expires_at)
    return deadline is not None and deadline <= now


def pool_status(rows: list[dict], now: datetime, soon_days: int) -> dict:
    """Server counts only; nothing is downloaded."""
    states = Counter(row["state"] for row in rows)
    copies = [row for row in rows if row["state"] != "ineligible"]
    soon = now + timedelta(days=soon_days)
    expiring = [row for row in copies if row.get("expires_at") and parse_time(row["expires_at"]) < soon]
    return {
        "copies": len(copies),
        "ready": states["ready"], "awaiting_review": states["awaiting_review"],
        "excluded": states["excluded"], "ineligible": states["ineligible"],
        "permanent_copies": sum(1 for row in copies if not row.get("expires_at")),
        f"v1_4_copies_expiring_{soon_days}_days": dict(sorted(Counter(row["state"] for row in expiring).items())),
        "ready_by_class": dict(sorted(Counter(row["label_class_id"] for row in rows if row["state"] == "ready").items())),
    }


def load_secret(mirror: Path) -> bytes:
    mirror.mkdir(parents=True, exist_ok=True)
    mirror.chmod(0o700)
    path = mirror / ".secret"
    if not path.exists():
        fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, "w") as stream:
            stream.write(secrets.token_hex(32))
    path.chmod(0o600)
    return bytes.fromhex(path.read_text().strip())


def keyed(secret: bytes, kind: str, value: str, length: int = 20) -> str:
    """Stable pseudonym: same secret and value give the same key; ids never leave the server in clear."""
    return hmac.new(secret, f"{kind}:{value}".encode(), hashlib.sha256).hexdigest()[:length]


def load_mirror(mirror: Path = MIRROR) -> dict:
    path = mirror / "manifest.json"
    if path.is_file():
        manifest = json.loads(path.read_text(encoding="utf-8"))
        if manifest.get("schema") != SCHEMA:
            raise ValueError(f"{path} em formato desconhecido; apague data/field/ e sincronize de novo.")
        return manifest
    return {"schema": SCHEMA, "synced_at": None, "secret_check": None, "photos": {}, "rejected": {}}


def save_mirror(mirror: Path, manifest: dict) -> None:
    temporary = mirror / "manifest.json.tmp"
    temporary.write_text(json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    temporary.replace(mirror / "manifest.json")


def _remove(mirror: Path, manifest: dict, key: str) -> None:
    (mirror / manifest["photos"].pop(key)["image"]).unlink(missing_ok=True)


def _place(mirror: Path, photo: dict, key: str) -> bool:
    """Keep each photo in its disease folder, like data/raw; True when the file moved."""
    target = f"{photo['class_id']}/{key}.jpg"
    if photo["image"] == target:
        return False
    (mirror / photo["class_id"]).mkdir(exist_ok=True)
    (mirror / photo["image"]).replace(mirror / target)
    photo["image"] = target
    return True


def _drop_empty_folders(mirror: Path) -> None:
    for folder in mirror.iterdir():
        if folder.is_dir() and not any(folder.iterdir()):
            folder.rmdir()


def prune_expired(mirror: Path = MIRROR, now: datetime | None = None) -> int:
    """Delete local copies past their v1.4 deadline, even without contacting the server."""
    now = now or datetime.now(timezone.utc)
    manifest = load_mirror(mirror)
    stale = [key for key, photo in manifest["photos"].items() if expired(photo["expires_at"], now)]
    for key in stale:
        _remove(mirror, manifest, key)
    if stale:
        save_mirror(mirror, manifest)
        _drop_empty_folders(mirror)
    return len(stale)


def mirror_status(mirror: Path = MIRROR, now: datetime | None = None) -> dict:
    manifest = load_mirror(mirror)
    photos = manifest["photos"].values()
    return {"synced_at": manifest["synced_at"], "photos": len(photos),
            "people": len({p["group"] for p in photos}),
            "expired_pending_removal": sum(1 for p in photos if expired(p["expires_at"], now or datetime.now(timezone.utc))),
            "by_class": dict(sorted(Counter(p["class_id"] for p in photos).items())),
            "rejected_by_quality": len(manifest["rejected"])}


def reencode(data: bytes, scratch: Path, min_side: int) -> bytes:
    """Decode like the app (EXIF orientation, sRGB, alpha on white), check quality, re-encode without metadata."""
    from PIL import Image

    from leafcare.preprocessing import decode_rgb
    if not data:
        raise ValueError("arquivo vazio")
    scratch.write_bytes(data)
    try:
        rgb = decode_rgb(scratch)
    finally:
        scratch.unlink(missing_ok=True)
    if min(rgb.shape[:2]) < min_side:
        raise ValueError(f"resolução abaixo de {min_side} px")
    buffer = io.BytesIO()
    Image.fromarray(rgb).save(buffer, format="JPEG", quality=95, optimize=True)
    return buffer.getvalue()


def describe(path: Path) -> dict:
    from leafcare.preprocessing import decode_rgb
    from training.dataset import difference_hash
    rgb = decode_rgb(path)
    return {"pixel_sha256": hashlib.sha256(str(rgb.shape).encode() + rgb.tobytes()).hexdigest(),
            "width": int(rgb.shape[1]), "height": int(rgb.shape[0]), "dhash": difference_hash(rgb)}


def sync(client: Supabase, config: dict, mirror: Path = MIRROR, now: datetime | None = None) -> dict:
    from PIL import Image
    now = now or datetime.now(timezone.utc)
    secret = load_secret(mirror)
    manifest = load_mirror(mirror)
    check = keyed(secret, "check", "leafcare-field-mirror", 16)
    if manifest["secret_check"] not in (None, check):
        # A new secret means new keys: rebuild instead of mixing pseudonyms.
        for key in list(manifest["photos"]):
            _remove(mirror, manifest, key)
        manifest["rejected"] = {}
    manifest["secret_check"] = check
    known = {photo["image"] for photo in manifest["photos"].values()}
    for leftover in mirror.rglob("*.jpg"):  # interrupted downloads never reached the manifest
        if leftover.relative_to(mirror).as_posix() not in known:
            leftover.unlink()

    rows = client.pool()
    ready = {keyed(secret, "photo", row["analysis_id"]): row for row in rows
             if row["state"] == "ready" and row.get("review_status") in REVIEWED and row.get("label_class_id")
             and not expired(row.get("expires_at"), now)}
    report = Counter()
    for key in [k for k in manifest["photos"] if k not in ready]:
        _remove(mirror, manifest, key)
        report["removed"] += 1
    manifest["rejected"] = {k: v for k, v in manifest["rejected"].items() if k in ready}
    for key, photo in manifest["photos"].items():
        row = ready[key]
        if (photo["class_id"], photo["review_status"]) != (row["label_class_id"], row["review_status"]):
            photo.update(class_id=row["label_class_id"], review_status=row["review_status"])
            report["relabeled"] += 1
        photo["expires_at"] = row.get("expires_at")
        _place(mirror, photo, key)
    contents = {photo["sha256"] for photo in manifest["photos"].values()}
    rejected = Counter()
    try:
        for key in sorted(set(ready) - set(manifest["photos"]) - set(manifest["rejected"])):
            row = ready[key]
            try:
                data = client.photo(row["object_path"])
            except (urllib.error.URLError, OSError):
                rejected["download_failed"] += 1  # transient: retried on the next sync
                continue
            try:
                content = reencode(data, mirror / ".download", config["min_image_side"])
            except (ValueError, OSError, Image.DecompressionBombError):
                manifest["rejected"][key] = "quality"  # same object, same result: not downloaded again
                rejected["quality"] += 1
                continue
            digest = hashlib.sha256(content).hexdigest()
            if digest in contents:  # re-encoding is deterministic: same pixels, same bytes
                rejected["duplicate"] += 1
                continue
            image = mirror / row["label_class_id"] / f"{key}.jpg"
            image.parent.mkdir(exist_ok=True)
            image.write_bytes(content)
            contents.add(digest)
            manifest["photos"][key] = {
                "image": image.relative_to(mirror).as_posix(), "class_id": row["label_class_id"], "review_status": row["review_status"],
                "group": keyed(secret, "person", row["user_id"], 16), "expires_at": row.get("expires_at"),
                "sha256": digest, **describe(image),
            }
            report["added"] += 1
    finally:
        manifest["synced_at"] = now.isoformat()
        save_mirror(mirror, manifest)
        _drop_empty_folders(mirror)
    return {"added": report["added"], "removed": report["removed"], "relabeled": report["relabeled"],
            "not_added": dict(sorted(rejected.items())), "pool": pool_status(rows, now, config["expiring_soon_days"]),
            "mirror": mirror_status(mirror, now)}


def tla_key(path: str) -> str:
    """Storage keys must be ASCII: a few TLA names contain Chinese ("副本", copy), stored as uXXXX."""
    return "".join(ch if ord(ch) < 128 else f"u{ord(ch):04x}" for ch in path)


def _tla_rows(manifest: Path) -> list[dict]:
    return json.loads(manifest.read_text(encoding="utf-8"))["rows"]


def _matches(path: Path, digest: str) -> bool:
    return path.is_file() and hashlib.sha256(path.read_bytes()).hexdigest() == digest


def ensure_tla(client: Supabase, manifest: Path = TLA_MANIFEST, raw: Path = TLA_ROOT) -> dict:
    """Restore missing or altered TLA images from the private bucket, verified against the manifest."""
    restored = 0
    for row in _tla_rows(manifest):
        path = raw / row["path"]
        if _matches(path, row["sha256"]):
            continue
        data = client.object(TLA_BUCKET, tla_key(row["path"]))
        if hashlib.sha256(data).hexdigest() != row["sha256"]:
            raise ValueError(f"{TLA_BUCKET}/{row['path']} difere do manifesto; não foi gravado.")
        path.parent.mkdir(parents=True, exist_ok=True)
        temporary = path.with_name(path.name + ".tmp")
        temporary.write_bytes(data)
        temporary.replace(path)
        restored += 1
    return {"tla_images": len(_tla_rows(manifest)), "restored": restored}


def upload_tla(client: Supabase, manifest: Path = TLA_MANIFEST, raw: Path = TLA_ROOT) -> dict:
    """Seed the private bucket with data/raw (one folder per disease); existing objects are kept."""
    rows = _tla_rows(manifest)
    wrong = [row["path"] for row in rows if not _matches(raw / row["path"], row["sha256"])]
    if wrong:
        raise SystemExit(f"{len(wrong)} imagens de data/raw ausentes ou diferentes do manifesto, ex.: {wrong[:3]}.")
    sent = sum(client.upload(TLA_BUCKET, tla_key(row["path"]), (raw / row["path"]).read_bytes()) for row in rows)
    return {"tla_images": len(rows), "uploaded": sent, "already_in_bucket": len(rows) - sent}
