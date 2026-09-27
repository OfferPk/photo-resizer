package com.offerpk.photoresizer.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import kotlin.math.max

/** A decoded source photo plus facts about the original file. */
class SourceImage(
    val uri: Uri,
    val bitmap: Bitmap,
    /** Dimensions of the original file (after EXIF rotation). */
    val originalWidth: Int,
    val originalHeight: Int,
    val originalBytes: Long,
    val mimeType: String,
    val displayName: String,
) {
    val baseName: String
        get() = displayName.substringBeforeLast('.').ifBlank { "photo" }
            .replace(Regex("[^A-Za-z0-9_-]+"), "_").take(40)
}

object ImageLoader {
    /** Largest edge kept in memory. Big enough for print-quality work, safe on low-RAM phones. */
    const val MAX_EDGE = 4096

    fun load(context: Context, uri: Uri, maxEdge: Int = MAX_EDGE): SourceImage {
        val cr = context.contentResolver
        var name = "photo.jpg"
        var size = -1L
        cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { c.getString(it) }?.let { name = it }
                c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }?.let { size = c.getLong(it) }
            }
        }
        if (size <= 0) {
            size = cr.openInputStream(uri)?.use { s ->
                var total = 0L
                val buf = ByteArray(64 * 1024)
                while (true) { val n = s.read(buf); if (n < 0) break; total += n }
                total
            } ?: 0L
        }
        val mime = cr.getType(uri) ?: "image/jpeg"

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unsupported or damaged image" }

        val orientation = runCatching {
            cr.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

        // Power-of-two subsampling keeps peak memory low on big (24–200 MP) photos.
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > maxEdge * 5 / 4) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        var bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: error("Could not decode image")

        val longEdge = max(bmp.width, bmp.height)
        if (longEdge > maxEdge) {
            val s = maxEdge.toFloat() / longEdge
            bmp = ImageOps.scale(bmp, (bmp.width * s).toInt(), (bmp.height * s).toInt())
        }
        bmp = applyOrientation(bmp, orientation)
        if (bmp.hasAlpha()) bmp = flattenOnWhite(bmp)

        val rotated = orientation in listOf(
            ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_ROTATE_270,
            ExifInterface.ORIENTATION_TRANSPOSE, ExifInterface.ORIENTATION_TRANSVERSE,
        )
        return SourceImage(
            uri = uri,
            bitmap = bmp,
            originalWidth = if (rotated) bounds.outHeight else bounds.outWidth,
            originalHeight = if (rotated) bounds.outWidth else bounds.outHeight,
            originalBytes = size,
            mimeType = mime,
            displayName = name,
        )
    }

    private fun applyOrientation(src: Bitmap, orientation: Int): Bitmap {
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            else -> return src
        }
        val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        if (out !== src) src.recycle()
        return out
    }

    private fun flattenOnWhite(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        Canvas(out).apply { drawColor(Color.WHITE); drawBitmap(src, 0f, 0f, null) }
        return out
    }
}
