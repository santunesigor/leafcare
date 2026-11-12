"""Test doubles shared by the sync, dataset and smoke tests."""
import io

import numpy as np
from PIL import Image

CONFIG = {"seed": 42, "field_split": {"train": 0.70, "validation": 0.15, "field_test": 0.15}, "min_field_groups": 5,
          "near_duplicate_hamming": 4, "min_image_side": 128, "expiring_soon_days": 14, "max_tla_macro_f1_drop": 0.02}


def picture(seed, size=(160, 144)):
    """Smooth random image: distinct seeds give distant difference hashes, robust to JPEG."""
    small = (np.random.default_rng(seed).random((8, 9, 3)) * 255).astype(np.uint8)
    return np.asarray(Image.fromarray(small).resize(size, Image.Resampling.BICUBIC)).copy()


def jpeg(rgb, exif=False):
    out = io.BytesIO()
    image = Image.fromarray(rgb)
    if exif:
        data = image.getexif()
        data[0x010F] = "private-camera"
        image.save(out, "JPEG", exif=data, quality=95)
    else:
        image.save(out, "JPEG", quality=95)
    return out.getvalue()


def row(analysis, user, label="frog_eye", state="ready", status="confirmed", expires=None):
    return {"analysis_id": analysis, "user_id": user, "object_path": f"{user}/{analysis}.jpg",
            "uploaded_at": "2026-11-01T00:00:00+00:00", "expires_at": expires, "predicted_class_id": "frog_eye",
            "confidence": 0.8, "model_sha256": "model-a", "analyzed_at": "2026-11-01T00:00:00+00:00",
            "review_status": status if state != "awaiting_review" else "pending",
            "label_class_id": label if state == "ready" else None, "state": state}


class FakeSupabase:
    """Serves training_pool() rows and photo bytes; records downloads."""

    def __init__(self, rows, photos):
        self.rows, self.photos, self.downloads = rows, photos, []

    def pool(self):
        return list(self.rows)

    def photo(self, object_path):
        self.downloads.append(object_path)
        data = self.photos[object_path.rsplit("/", 1)[1].removesuffix(".jpg")]
        if isinstance(data, Exception):
            raise data
        return data
