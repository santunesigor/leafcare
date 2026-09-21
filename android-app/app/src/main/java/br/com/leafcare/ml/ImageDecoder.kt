package br.com.leafcare.ml

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File

object ImageDecoder {
    fun decode(file: File): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Imagem inválida ou formato não suportado." }
        require(bounds.outMimeType in setOf("image/jpeg", "image/png", "image/webp", "image/bmp", "image/x-ms-bmp")) {
            "Use uma imagem JPEG, PNG, BMP ou WebP."
        }
        require(bounds.outWidth.toLong() * bounds.outHeight <= 16_000_000) { "Imagem acima de 16 megapixels. Escolha uma cópia menor." }
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
            inScaled = false
        }
        val bitmap = requireNotNull(BitmapFactory.decodeFile(file.path, options)) { "Não foi possível abrir a imagem." }
        val orientation = runCatching { ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)
        val matrix = Matrix().apply {
            when (orientation) {
                2 -> setScale(-1f, 1f)
                3 -> setRotate(180f)
                4 -> setScale(1f, -1f)
                5 -> { setRotate(90f); postScale(-1f, 1f) }
                6 -> setRotate(90f)
                7 -> { setRotate(-90f); postScale(-1f, 1f) }
                8 -> setRotate(-90f)
            }
        }
        if (matrix.isIdentity) return bitmap
        val oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, false)
        if (oriented !== bitmap) bitmap.recycle()
        return oriented
    }
}
