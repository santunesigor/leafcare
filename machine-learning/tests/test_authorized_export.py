import io
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from PIL import Image
from export_authorized_training import export, valid_image


def jpeg(size=(180, 160)):
    out = io.BytesIO()
    Image.new("RGB", size, "green").save(out, "JPEG")
    return out.getvalue()


class AuthorizedExportTest(unittest.TestCase):
    def test_quality_gate(self):
        self.assertEqual(valid_image(jpeg()).size, (180, 160))
        with self.assertRaisesRegex(ValueError, "resolução"):
            valid_image(jpeg((64, 64)))
        with self.assertRaisesRegex(ValueError, "vazio"):
            valid_image(b"")

    def test_export_is_disabled_until_legal_documents_are_published(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "output"
            output.mkdir()
            (output / "old.jpg").write_bytes(b"stale")
            with self.assertRaisesRegex(RuntimeError, "desativada"):
                export("https://example.test", "secret", output)
            self.assertFalse(output.exists())

    def test_export_requires_matching_current_consent_and_review(self):
        rows = [
            {"analysis_id": "a", "terms_version": "1.0", "training_consents": {"granted": True, "terms_version": "1.0"},
             "analyses": {"user_id": "private-user", "deleted_at": None, "photo_reviews": {"status": "corrected", "class_id": "frog_eye"}}},
            {"analysis_id": "b", "terms_version": "old", "training_consents": {"granted": True, "terms_version": "new"},
             "analyses": {"user_id": "private-user", "deleted_at": None, "photo_reviews": {"status": "confirmed", "class_id": "frog_eye"}}},
            {"analysis_id": "c", "terms_version": "1.0", "training_consents": {"granted": True, "terms_version": "1.0"},
             "analyses": {"user_id": "private-user", "deleted_at": None, "photo_reviews": {"status": "uncertain", "class_id": "frog_eye"}}},
        ]
        duplicate = dict(rows[0], analysis_id="duplicate")
        rows.append(duplicate)
        def fake_request(url, key):
            return json.dumps(rows).encode() if "/rest/v1/" in url else jpeg()
        with tempfile.TemporaryDirectory() as directory:
            with patch("export_authorized_training.TRAINING_EXPORT_ENABLED", True), patch("export_authorized_training.request", side_effect=fake_request):
                result = export("https://example.test", "secret-not-logged", Path(directory) / "output")
            self.assertEqual(result, {"exported": 1, "rejected": 3})
            manifest = json.loads((Path(directory) / "output" / "manifest.json").read_text())
            self.assertEqual(manifest["rejected"], {"consent_version": 1, "review": 1, "duplicate": 1, "download_or_quality": 0})
            self.assertEqual(manifest["images"][0]["class_id"], "frog_eye")
            self.assertNotIn("private-user", json.dumps(manifest))
            images = list((Path(directory) / "output").glob("*.jpg"))
            self.assertEqual(len(images), 1)
            with Image.open(images[0]) as exported:
                self.assertEqual(len(exported.getexif()), 0)

    def test_failed_refresh_clears_previous_export(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "output"
            output.mkdir()
            (output / "old.jpg").write_bytes(b"private")
            with patch("export_authorized_training.TRAINING_EXPORT_ENABLED", True), patch("export_authorized_training.request", side_effect=OSError("offline")):
                with self.assertRaises(OSError):
                    export("https://example.test", "secret", output)
            self.assertFalse(output.exists())


if __name__ == "__main__":
    unittest.main()
