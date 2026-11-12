"""Local field mirror: reconciliation with training_pool(), privacy and the secret file."""
import io
import json
import stat
import urllib.error
from datetime import datetime, timezone
from unittest.mock import patch

import pytest
from PIL import Image

from fakes import CONFIG, FakeSupabase, jpeg, picture, row
from training import sync

NOW = datetime(2026, 11, 12, tzinfo=timezone.utc)


def mirror_files(mirror):
    return sorted(p.name for p in mirror.glob("*.jpg"))


def test_first_sync_downloads_ready_photos_without_metadata_or_ids(tmp_path):
    mirror = tmp_path / "field"
    rows = [row("analysis-1", "user-secret-1", label="healthy", status="corrected"),
            row("analysis-2", "user-secret-1"), row("analysis-3", "user-secret-2"),
            row("analysis-4", "user-secret-3", state="awaiting_review"),
            row("analysis-5", "user-secret-3", state="excluded", status="uncertain"),
            row("analysis-6", "user-secret-3", state="ineligible")]
    photos = {f"analysis-{i}": jpeg(picture(i), exif=True) for i in range(1, 7)}
    client = FakeSupabase(rows, photos)
    report = sync.sync(client, CONFIG, mirror, NOW)

    assert report["added"] == 3 and report["removed"] == 0
    assert sorted(client.downloads) == ["user-secret-1/analysis-1.jpg", "user-secret-1/analysis-2.jpg",
                                        "user-secret-2/analysis-3.jpg"]
    manifest = json.loads((mirror / "manifest.json").read_text())
    text = (mirror / "manifest.json").read_text()
    for secret in ("user-secret", "analysis-"):
        assert secret not in text
        assert not any(secret in name for name in mirror_files(mirror))
    labels = sorted(p["class_id"] for p in manifest["photos"].values())
    assert labels == ["frog_eye", "frog_eye", "healthy"]
    assert len({p["group"] for p in manifest["photos"].values()}) == 2, "same person shares a group"
    for name in mirror_files(mirror):
        with Image.open(mirror / name) as image:
            assert len(image.getexif()) == 0
    assert stat.S_IMODE((mirror / ".secret").stat().st_mode) == 0o600
    assert stat.S_IMODE(mirror.stat().st_mode) == 0o700
    assert report["pool"]["ready"] == 3 and report["pool"]["awaiting_review"] == 1


def test_resync_removes_relabels_and_adds_without_redownloading(tmp_path):
    mirror = tmp_path / "field"
    photos = {name: jpeg(picture(i)) for i, name in enumerate(["keep", "relabel", "revoke", "expire", "new"])}
    rows = [row("keep", "u1"), row("relabel", "u1"), row("revoke", "u2"),
            row("expire", "u3", expires="2026-11-20T00:00:00+00:00")]
    first = FakeSupabase(rows, photos)
    sync.sync(first, CONFIG, mirror, NOW)
    before = json.loads((mirror / "manifest.json").read_text())["photos"]
    assert len(before) == 4

    later = datetime(2026, 11, 25, tzinfo=timezone.utc)  # past the v1.4 deadline of "expire"
    second = FakeSupabase([row("keep", "u1"), row("relabel", "u1", label="healthy", status="corrected"),
                           row("revoke", "u2", state="excluded", status="rejected"),
                           row("expire", "u3", expires="2026-11-20T00:00:00+00:00"), row("new", "u4")], photos)
    report = sync.sync(second, CONFIG, mirror, later)

    assert (report["added"], report["removed"], report["relabeled"]) == (1, 2, 1)
    assert second.downloads == ["u4/new.jpg"], "relabel must not download again"
    after = json.loads((mirror / "manifest.json").read_text())["photos"]
    assert len(after) == 3 and len(mirror_files(mirror)) == 3
    keys_kept = set(before) & set(after)
    assert len(keys_kept) == 2, "keys are stable across syncs"
    assert sorted(after[k]["class_id"] for k in keys_kept) == ["frog_eye", "healthy"]
    assert all(before[k]["group"] == after[k]["group"] for k in keys_kept)


def test_quality_rejections_are_remembered_and_duplicates_skipped(tmp_path):
    mirror = tmp_path / "field"
    same = jpeg(picture(1))
    rows = [row("a", "u1"), row("b", "u2"), row("small", "u3"), row("broken", "u3"), row("offline", "u4")]
    photos = {"a": same, "b": same, "small": jpeg(picture(2, (64, 64))), "broken": b"not an image",
              "offline": urllib.error.URLError("down")}
    report = sync.sync(FakeSupabase(rows, photos), CONFIG, mirror, NOW)
    assert report["added"] == 1
    assert report["not_added"] == {"download_failed": 1, "duplicate": 1, "quality": 2}

    again = FakeSupabase(rows, {**photos, "offline": jpeg(picture(3))})
    sync.sync(again, CONFIG, mirror, NOW)
    # The duplicate (a or b) and the transient failure are retried; quality failures are not downloaded again.
    assert len(again.downloads) == 2 and "u4/offline.jpg" in again.downloads
    assert not {"u3/small.jpg", "u3/broken.jpg"} & set(again.downloads)


def test_secret_permissions_are_restricted_and_a_new_secret_rebuilds_the_mirror(tmp_path):
    mirror = tmp_path / "field"
    mirror.mkdir()
    (mirror / ".secret").write_text("ab" * 32)
    (mirror / ".secret").chmod(0o644)
    assert sync.load_secret(mirror) == bytes.fromhex("ab" * 32)
    assert stat.S_IMODE((mirror / ".secret").stat().st_mode) == 0o600

    client = FakeSupabase([row("a", "u1")], {"a": jpeg(picture(1))})
    sync.sync(client, CONFIG, mirror, NOW)
    old = set(json.loads((mirror / "manifest.json").read_text())["photos"])
    (mirror / ".secret").unlink()
    sync.sync(client, CONFIG, mirror, NOW)
    new = set(json.loads((mirror / "manifest.json").read_text())["photos"])
    assert old.isdisjoint(new) and len(new) == 1 and len(mirror_files(mirror)) == 1


def test_expired_copies_are_pruned_offline_and_leftovers_cleaned(tmp_path):
    mirror = tmp_path / "field"
    rows = [row("a", "u1", expires="2026-11-20T00:00:00+00:00"), row("b", "u1")]
    client = FakeSupabase(rows, {"a": jpeg(picture(1)), "b": jpeg(picture(2))})
    sync.sync(client, CONFIG, mirror, NOW)
    assert sync.prune_expired(mirror, datetime(2026, 11, 21, tzinfo=timezone.utc)) == 1
    assert len(mirror_files(mirror)) == 1
    (mirror / "interrupted.jpg").write_bytes(b"partial")
    sync.sync(FakeSupabase([row("b", "u1")], {}), CONFIG, mirror, NOW)
    assert len(mirror_files(mirror)) == 1


def test_status_counts_states_classes_and_v14_copies_about_to_expire():
    rows = [row("a", "u", state="awaiting_review", expires="2026-11-20T00:00:00+00:00"),
            row("b", "u", state="awaiting_review", expires="2026-12-30T00:00:00+00:00"),
            row("c", "u", expires="2026-11-15T00:00:00+00:00"), row("d", "u", label="healthy"),
            row("e", "u", state="ineligible", expires="2026-11-13T00:00:00+00:00")]
    status = sync.pool_status(rows, NOW, 14)
    assert status["copies"] == 4 and status["ready"] == 2 and status["ineligible"] == 1
    assert status["v1_4_copies_expiring_14_days"] == {"awaiting_review": 1, "ready": 1}
    assert status["permanent_copies"] == 1
    assert status["ready_by_class"] == {"frog_eye": 1, "healthy": 1}


def test_client_pages_the_pool_and_keeps_the_key_out_of_urls():
    pages = [[{"analysis_id": "1"}, {"analysis_id": "2"}], [{"analysis_id": "3"}], []]
    seen = []

    def urlopen(request, timeout):
        seen.append(request)
        return io.BytesIO(json.dumps(pages[len(seen) - 1]).encode())

    with patch("urllib.request.urlopen", side_effect=urlopen):
        rows = sync.Supabase("https://example.test/", "service-key").pool()
    assert [r["analysis_id"] for r in rows] == ["1", "2", "3"]
    assert [r.full_url.rsplit("offset=", 1)[1] for r in seen] == ["0", "2", "3"]
    assert all("service-key" not in r.full_url and r.get_header("Authorization") == "Bearer service-key" for r in seen)


def test_missing_credentials_stop_without_echoing_values(monkeypatch):
    monkeypatch.delenv("SUPABASE_URL", raising=False)
    monkeypatch.setenv("SUPABASE_SERVICE_ROLE_KEY", "do-not-print")
    with pytest.raises(SystemExit) as error:
        sync.credentials()
    assert "do-not-print" not in str(error.value)
