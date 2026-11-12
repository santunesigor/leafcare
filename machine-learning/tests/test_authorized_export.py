import io
import json
import tempfile
import unittest
from datetime import datetime, timezone
from pathlib import Path
from unittest.mock import patch

from PIL import Image

from export_authorized_training import cleanup_expired_export, export, group_key, pool_status, valid_image

NOW = datetime(2026, 11, 12, tzinfo=timezone.utc)


def jpeg(size=(180, 160), color="green", exif=False):
    out = io.BytesIO()
    image = Image.new("RGB", size, color)
    if exif:
        data = image.getexif()
        data[0x010F] = "private-camera"  # Make
        image.save(out, "JPEG", exif=data)
    else:
        image.save(out, "JPEG")
    return out.getvalue()


def pool_row(analysis, user="private-user", state="ready", status="confirmed", label="frog_eye", expires="2026-12-01T00:00:00+00:00"):
    return {"analysis_id": analysis, "user_id": user, "object_path": f"{user}/{analysis}.jpg", "uploaded_at": "2026-09-02T00:00:00+00:00",
            "expires_at": expires, "predicted_class_id": "frog_eye", "confidence": 0.8, "model_sha256": "model-a",
            "analyzed_at": "2026-09-02T00:00:00+00:00", "review_status": status, "label_class_id": label, "state": state}


class AuthorizedExportTest(unittest.TestCase):
    def run_export(self, rows, images):
        def fake_request(url, key, body=None):
            if "/rpc/training_pool" in url:
                return json.dumps(rows).encode()
            return images[url.rsplit("/", 1)[1].removesuffix(".jpg")]
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        output = Path(directory.name) / "output"
        with patch("export_authorized_training.request", side_effect=fake_request):
            result = export("https://example.test", "secret-not-logged", output, NOW)
        return result, output, json.loads((output / "manifest.json").read_text())

    def test_local_export_is_removed_after_30_days(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "output"
            output.mkdir()
            (output / "manifest.json").write_text(json.dumps({"exported_at": "2026-09-01T00:00:00+00:00"}))
            self.assertTrue(cleanup_expired_export(output, datetime(2026, 10, 1, tzinfo=timezone.utc)))
            self.assertFalse(output.exists())

    def test_local_export_is_kept_before_30_days(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "output"
            output.mkdir()
            (output / "manifest.json").write_text(json.dumps({"exported_at": "2026-09-15T00:00:00+00:00"}))
            self.assertFalse(cleanup_expired_export(output, datetime(2026, 10, 1, tzinfo=timezone.utc)))
            self.assertTrue(output.exists())

    def test_quality_gate(self):
        with tempfile.TemporaryDirectory() as directory:
            scratch = Path(directory) / "scratch"
            self.assertEqual(valid_image(jpeg(), scratch).size, (180, 160))
            with self.assertRaisesRegex(ValueError, "resolução"):
                valid_image(jpeg((64, 64)), scratch)
            with self.assertRaisesRegex(ValueError, "vazio"):
                valid_image(b"", scratch)
            self.assertFalse(scratch.exists())

    def test_export_honors_explicit_configuration_off_switch(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "output"
            output.mkdir()
            (output / "old.jpg").write_bytes(b"stale")
            with patch("export_authorized_training.TRAINING_EXPORT_ENABLED", False):
                with self.assertRaisesRegex(RuntimeError, "desativada"):
                    export("https://example.test", "secret", output)
            self.assertFalse(output.exists())

    def test_only_ready_copies_are_downloaded_with_their_reviewed_label(self):
        rows = [pool_row("a", label="healthy", status="corrected"), pool_row("b", state="awaiting_review", status="pending", label=None),
                pool_row("c", state="excluded", status="uncertain", label=None), pool_row("d", state="ineligible")]
        result, output, manifest = self.run_export(rows, {"a": jpeg(exif=True)})
        self.assertEqual(result["exported"], 1)
        self.assertEqual(manifest["images"][0]["class_id"], "healthy")
        self.assertEqual(manifest["images"][0]["predicted_class_id"], "frog_eye")
        self.assertEqual(manifest["images"][0]["review_status"], "corrected")
        self.assertEqual(manifest["pool"]["awaiting_review"], 1)
        self.assertEqual(manifest["pool"]["ineligible"], 1)
        self.assertEqual(manifest["delete_after"], "2026-12-12T00:00:00+00:00")
        images = list(output.glob("*.jpg"))
        self.assertEqual(len(images), 1)
        with Image.open(images[0]) as exported:
            self.assertEqual(len(exported.getexif()), 0)

    def test_manifest_has_no_account_or_analysis_identifiers(self):
        rows = [pool_row("analysis-1", user="user-secret-1"), pool_row("analysis-2", user="user-secret-1"),
                pool_row("analysis-3", user="user-secret-2")]
        images = {"analysis-1": jpeg(color="green"), "analysis-2": jpeg(color="red"), "analysis-3": jpeg(color="blue")}
        _, output, manifest = self.run_export(rows, images)
        text = (output / "manifest.json").read_text()
        for secret in ("user-secret", "analysis-"):
            self.assertNotIn(secret, text)
            self.assertFalse(any(secret in p.name for p in output.iterdir()))
        groups = [image["group_id"] for image in manifest["images"]]
        self.assertEqual(len(set(groups)), 2, "same owner shares a group, different owners do not")

    def test_groups_are_unlinkable_between_exports(self):
        self.assertNotEqual(group_key(b"a" * 32, "user"), group_key(b"b" * 32, "user"))
        self.assertEqual(group_key(b"a" * 32, "user"), group_key(b"a" * 32, "user"))

    def test_duplicates_and_bad_images_are_rejected(self):
        rows = [pool_row("a"), pool_row("b"), pool_row("c")]
        _, output, manifest = self.run_export(rows, {"a": jpeg(), "b": jpeg(), "c": jpeg((64, 64))})
        self.assertEqual(len(manifest["images"]), 1)
        self.assertEqual(manifest["rejected"], {"duplicate": 1, "download_or_quality": 1})
        self.assertEqual(len(list(output.glob("*.jpg"))), 1)

    def test_status_counts_copies_about_to_expire(self):
        rows = [pool_row("a", state="awaiting_review", expires="2026-11-20T00:00:00+00:00"),
                pool_row("b", state="awaiting_review", expires="2026-12-30T00:00:00+00:00"), pool_row("c")]
        status = pool_status(rows, NOW)
        self.assertEqual(status["awaiting_review_expiring_14_days"], 1)
        self.assertEqual(status["ready_by_class"], {"frog_eye": 1})
        self.assertEqual(status["copies"], 3)

    def test_failed_refresh_clears_previous_export(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "output"
            output.mkdir()
            (output / "old.jpg").write_bytes(b"private")
            with patch("export_authorized_training.request", side_effect=OSError("offline")):
                with self.assertRaises(OSError):
                    export("https://example.test", "secret", output)
            self.assertFalse(output.exists())


if __name__ == "__main__":
    unittest.main()
