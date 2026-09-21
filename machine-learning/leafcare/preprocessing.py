"""Contrato idêntico a ml/PixelPreprocessor.kt (RGB uint8 -> float32)."""
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


def resize_rgb(rgb, size=224):
    """Recorte central; bilinear com coordenadas half-pixel e arredondamento inteiro.

    Nenhum filtro ou normalização implícito de Pillow/Bitmap é utilizado.
    """
    if rgb.ndim != 3 or rgb.shape[2] != 3 or rgb.dtype != np.uint8:
        raise ValueError("Esperado RGB uint8 H×W×3.")
    height, width, _ = rgb.shape
    side = min(width, height)
    if side < 1 or size < 1:
        raise ValueError("Imagem vazia.")
    left, top = (width - side) // 2, (height - side) // 2
    cropped = rgb[top:top + side, left:left + side].astype(np.int64)
    denominator = 2 * size
    positions = np.clip((2 * np.arange(size) + 1) * side - size, 0, (side - 1) * denominator)
    lo, fraction = positions // denominator, positions % denominator
    hi = np.minimum(lo + 1, side - 1)
    wx = fraction[None, :, None]
    wy = fraction[:, None, None]
    upper = cropped[lo[:, None], lo[None, :]] * (denominator - wx) + cropped[lo[:, None], hi[None, :]] * wx
    lower = cropped[hi[:, None], lo[None, :]] * (denominator - wx) + cropped[hi[:, None], hi[None, :]] * wx
    divisor = denominator * denominator
    result = (upper * (denominator - wy) + lower * wy + divisor // 2) // divisor
    return result.astype(np.float32)


def preprocess(path, size=224):
    return resize_rgb(decode_rgb(path), size)[None, ...]
