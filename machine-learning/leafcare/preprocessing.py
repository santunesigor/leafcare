"""App image contract: decode like Android, then RGB uint8 -> float32 [1,224,224,3]."""
from pathlib import Path
from io import BytesIO
import numpy as np
from PIL import Image, ImageOps, ImageCms

MAX_PIXELS = 16_000_000
FORMATS = {"JPEG", "PNG", "WEBP", "BMP"}


def decode_rgb(path):
    with Image.open(Path(path)) as source:
        if source.format not in FORMATS or getattr(source, "n_frames", 1) != 1:
            raise ValueError("Use JPEG, PNG, BMP ou WebP estático.")
        if source.width * source.height > MAX_PIXELS:
            raise ValueError("Imagem acima de 16 megapixels; exporte uma cópia menor.")
        source.load()
        oriented = ImageOps.exif_transpose(source)
        profile = source.info.get("icc_profile")
        if profile:
            # BitmapFactory no Android também decodifica para sRGB.
            alpha = oriented.convert("RGBA").getchannel("A")
            try:
                oriented = ImageCms.profileToProfile(oriented.convert("RGB"),
                    ImageCms.ImageCmsProfile(BytesIO(profile)), ImageCms.createProfile("sRGB"), outputMode="RGB")
            except (ImageCms.PyCMSError, OSError) as error:
                raise ValueError("Perfil de cor inválido; exporte a imagem como sRGB.") from error
            if alpha.getextrema() != (255, 255):
                oriented.putalpha(alpha)
        if oriented.mode in ("RGBA", "LA") or "transparency" in oriented.info:
            rgba = np.asarray(oriented.convert("RGBA"), dtype=np.uint32)
            a = rgba[..., 3:4]
            return ((rgba[..., :3] * a + 255 * (255 - a) + 127) // 255).astype(np.uint8)
        return np.asarray(oriented.convert("RGB"), dtype=np.uint8).copy()


DISTILLED_RESIZE = "resize_shorter_256_bicubic_center_crop_224_v1"


def resize_distilled_rgb(rgb):
    """Preprocessing do checkpoint: Pillow bicubic antialias + CenterCrop torchvision."""
    if rgb.ndim != 3 or rgb.shape[2] != 3 or rgb.dtype != np.uint8:
        raise ValueError("Esperado RGB uint8 H×W×3.")
    height, width, _ = rgb.shape
    short = min(width, height)
    if short < 1:
        raise ValueError("Imagem vazia.")
    resized_width, resized_height = 256 * width // short, 256 * height // short
    image = Image.fromarray(rgb).resize((resized_width, resized_height), Image.Resampling.BICUBIC)
    left, top = round((resized_width - 224) / 2), round((resized_height - 224) / 2)
    return np.asarray(image.crop((left, top, left + 224, top + 224)), dtype=np.float32).copy()


def preprocess(path):
    return resize_distilled_rgb(decode_rgb(path))[None, ...]
