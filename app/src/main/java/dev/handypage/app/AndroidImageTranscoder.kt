package dev.handypage.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dev.handypage.app.engine.ImageTranscoder
import java.io.ByteArrayOutputStream

/**
 * M30: BitmapFactory-backed [ImageTranscoder] for the article-image embed
 * pipeline. Readium's resource serving fails webp in practice (jpg/png embeds
 * render, webp shows the broken-image icon — verified on-device, M30), so
 * webp/avif payloads are normalized: JPEG (q92) for opaque photos, PNG when
 * the bitmap carries alpha. Undecodable input (e.g. avif on pre-Android 12)
 * returns null and the embedder keeps the original bytes.
 */
object AndroidImageTranscoder : ImageTranscoder {
    override fun toSafeFormat(bytes: ByteArray, fromExt: String): Pair<ByteArray, String>? {
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val out = ByteArrayOutputStream()
        return if (bmp.hasAlpha()) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray() to "png"
        } else {
            bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
            out.toByteArray() to "jpg"
        }
    }
}
