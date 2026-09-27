package com.offerpk.photoresizer.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.offerpk.photoresizer.core.JpegEncoder
import com.offerpk.photoresizer.core.SheetLayout
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object ImageOps {
    private val filterPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)

    /** High-quality scale (halves repeatedly for big reductions to avoid aliasing). */
    fun scale(src: Bitmap, w: Int, h: Int): Bitmap {
        val tw = max(1, w); val th = max(1, h)
        if (tw == src.width && th == src.height) return src
        var cur = src
        while (cur.width / 2 >= tw && cur.height / 2 >= th && cur.width / 2 > 0) {
            val next = Bitmap.createScaledBitmap(cur, cur.width / 2, cur.height / 2, true)
            if (cur !== src) cur.recycle()
            cur = next
        }
        val out = Bitmap.createScaledBitmap(cur, tw, th, true)
        if (cur !== src && cur !== out) cur.recycle()
        return out
    }

    fun jpeg(bitmap: Bitmap, quality: Int): ByteArray =
        ByteArrayOutputStream(64 * 1024).also { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()

    fun png(bitmap: Bitmap): ByteArray =
        ByteArrayOutputStream(64 * 1024).also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

    /**
     * Writes the print resolution into a JPEG's JFIF APP0 header (if present) so
     * printers and editors know the physical size, e.g. 413x531 px @300 DPI = 35x45 mm.
     */
    fun withJpegDpi(bytes: ByteArray, dpi: Int): ByteArray {
        if (bytes.size < 18) return bytes
        val isJfif = bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte() && bytes[3] == 0xE0.toByte() &&
            bytes[6] == 'J'.code.toByte() && bytes[7] == 'F'.code.toByte() &&
            bytes[8] == 'I'.code.toByte() && bytes[9] == 'F'.code.toByte() && bytes[10] == 0.toByte()
        if (!isJfif) return bytes
        val out = bytes.copyOf()
        out[13] = 1 // units: dots per inch
        out[14] = (dpi shr 8).toByte(); out[15] = dpi.toByte()
        out[16] = (dpi shr 8).toByte(); out[17] = dpi.toByte()
        return out
    }

    /** JpegEncoder backed by Bitmap.compress, caching the last scaled bitmap. */
    class BitmapJpegEncoder(private val src: Bitmap) : JpegEncoder {
        override val sourceWidth get() = src.width
        override val sourceHeight get() = src.height
        private var cached: Bitmap? = null

        override fun encode(width: Int, height: Int, quality: Int): ByteArray {
            val bmp = if (width == src.width && height == src.height) src else {
                cached?.takeIf { it.width == width && it.height == height } ?: run {
                    cached?.let { if (it !== src) it.recycle() }
                    scale(src, width, height).also { cached = it }
                }
            }
            return jpeg(bmp, quality)
        }
    }

    /** Crops [srcRect] (source pixels, may extend outside) into an exact [outW]x[outH] bitmap on white. */
    fun cropTo(src: Bitmap, srcRect: RectF, outW: Int, outH: Int): Bitmap {
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(Color.WHITE)
        // Pre-scale for quality when shrinking a lot
        val k = outW / srcRect.width()
        var bmp = src
        var rect = RectF(srcRect)
        if (k < 0.5f) {
            val f = max(k * 2f, 0.01f)
            bmp = scale(src, (src.width * f).roundToInt(), (src.height * f).roundToInt())
            val fx = bmp.width.toFloat() / src.width
            val fy = bmp.height.toFloat() / src.height
            rect = RectF(srcRect.left * fx, srcRect.top * fy, srcRect.right * fx, srcRect.bottom * fy)
        }
        val m = android.graphics.Matrix()
        m.setRectToRect(rect, RectF(0f, 0f, outW.toFloat(), outH.toFloat()), android.graphics.Matrix.ScaleToFit.FILL)
        c.drawBitmap(bmp, m, filterPaint)
        if (bmp !== src) bmp.recycle()
        return out
    }

    fun centerCrop(src: Bitmap, outW: Int, outH: Int): Bitmap {
        val targetAspect = outW.toFloat() / outH
        val srcAspect = src.width.toFloat() / src.height
        val rect = if (srcAspect > targetAspect) {
            val w = src.height * targetAspect
            RectF((src.width - w) / 2f, 0f, (src.width + w) / 2f, src.height.toFloat())
        } else {
            val h = src.width / targetAspect
            RectF(0f, (src.height - h) / 2f, src.width.toFloat(), (src.height + h) / 2f)
        }
        return cropTo(src, rect, outW, outH)
    }

    /**
     * Makes a plain, light background pure white: flood-fills from the image border
     * over pixels close in colour to their neighbours. Works best with a plain wall.
     * [tolerance] 0..100.
     */
    fun whitenBackground(src: Bitmap, tolerance: Int): Bitmap {
        val w = src.width; val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val visited = BooleanArray(w * h)
        val tol = 8 + tolerance * 0.9 // per-step colour distance
        val maxDrift = 30 + tolerance * 1.6 // distance from the seed colour
        val queue = IntArray(w * h)
        var qh = 0; var qt = 0

        fun dist(a: Int, b: Int): Double {
            val dr = ((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)
            val dg = ((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)
            val db = (a and 0xFF) - (b and 0xFF)
            return kotlin.math.sqrt((dr * dr + dg * dg + db * db).toDouble())
        }
        fun luma(c: Int) = (((c shr 16) and 0xFF) * 299 + ((c shr 8) and 0xFF) * 587 + (c and 0xFF) * 114) / 1000

        // Seed colour = median-ish of border pixels (use the brightest common one)
        val border = ArrayList<Int>()
        for (x in 0 until w step max(1, w / 50)) { border += px[x]; border += px[(h - 1) * w + x] }
        for (y in 0 until h step max(1, h / 50)) { border += px[y * w]; border += px[y * w + w - 1] }
        border.sortBy { luma(it) }
        val seed = border[border.size / 2]

        fun push(i: Int) { if (!visited[i]) { visited[i] = true; queue[qt++] = i } }
        for (x in 0 until w) { push(x); push((h - 1) * w + x) }
        for (y in 0 until h) { push(y * w); push(y * w + w - 1) }
        // drop border seeds that don't look like background
        val fill = BooleanArray(w * h)
        while (qh < qt) {
            val i = queue[qh++]
            val c = px[i]
            if (dist(c, seed) > maxDrift) continue
            fill[i] = true
            val x = i % w; val y = i / w
            if (x > 0) { val j = i - 1; if (!visited[j] && dist(px[j], c) <= tol) push(j) }
            if (x < w - 1) { val j = i + 1; if (!visited[j] && dist(px[j], c) <= tol) push(j) }
            if (y > 0) { val j = i - w; if (!visited[j] && dist(px[j], c) <= tol) push(j) }
            if (y < h - 1) { val j = i + w; if (!visited[j] && dist(px[j], c) <= tol) push(j) }
        }
        // soften the edge: pixels next to the filled area are blended half-way to white
        val out = px.copyOf()
        for (i in 0 until w * h) {
            if (fill[i]) { out[i] = Color.WHITE; continue }
            val x = i % w; val y = i / w
            var n = 0
            if (x > 0 && fill[i - 1]) n++
            if (x < w - 1 && fill[i + 1]) n++
            if (y > 0 && fill[i - w]) n++
            if (y < h - 1 && fill[i + w]) n++
            if (n > 0) {
                val a = 0.18f * n
                val c = px[i]
                val r = ((c shr 16) and 0xFF); val g = ((c shr 8) and 0xFF); val b = c and 0xFF
                out[i] = Color.rgb(
                    (r + (255 - r) * a).toInt(), (g + (255 - g) * a).toInt(), (b + (255 - b) * a).toInt(),
                )
            }
        }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.setPixels(out, 0, w, 0, 0, w, h)
        return bmp
    }

    /** Tiles copies of [photo] on a 6x4 inch (1800x1200 @300 DPI) sheet with light cut guides. */
    fun printSheet(photo: Bitmap, sheetW: Int = 1800, sheetH: Int = 1200): Pair<Bitmap, Int> {
        // choose orientation that fits more copies
        val land = SheetLayout.layout(sheetW, sheetH, photo.width, photo.height, minGap = 12)
        val port = SheetLayout.layout(sheetH, sheetW, photo.width, photo.height, minGap = 12)
        val (w, h, places) = if (port.size > land.size) Triple(sheetH, sheetW, port) else Triple(sheetW, sheetH, land)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(Color.WHITE)
        val guide = Paint().apply { color = Color.rgb(190, 190, 190); strokeWidth = 2f; style = Paint.Style.STROKE }
        for (p in places) {
            c.drawBitmap(photo, p.x.toFloat(), p.y.toFloat(), null)
            c.drawRect(p.x - 1f, p.y - 1f, p.x + photo.width + 1f, p.y + photo.height + 1f, guide)
        }
        return out to places.size
    }

    // ---------------- Signature cleaning ----------------

    fun toLuma(src: Bitmap): IntArray {
        val w = src.width; val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        for (i in px.indices) {
            val c = px[i]
            px[i] = (((c shr 16) and 0xFF) * 299 + ((c shr 8) and 0xFF) * 587 + (c and 0xFF) * 114) / 1000
        }
        return px
    }

    /** Otsu's method: threshold that best separates ink from paper. */
    fun otsuThreshold(luma: IntArray): Int {
        val hist = IntArray(256)
        luma.forEach { hist[it]++ }
        val total = luma.size.toDouble()
        var sum = 0.0
        for (i in 0..255) sum += i * hist[i].toDouble()
        var sumB = 0.0; var wB = 0.0; var best = 0.0; var t = 128
        for (i in 0..255) {
            wB += hist[i]; if (wB == 0.0) continue
            val wF = total - wB; if (wF == 0.0) break
            sumB += i * hist[i].toDouble()
            val mB = sumB / wB; val mF = (sum - sumB) / wF
            val between = wB * wF * (mB - mF) * (mB - mF)
            if (between > best) { best = between; t = i }
        }
        return t
    }

    /**
     * Local background normalisation: divides each pixel by a blurred estimate of the
     * paper brightness, which removes shadows / uneven lighting before thresholding.
     */
    fun normalizeLighting(luma: IntArray, w: Int, h: Int): IntArray {
        val r = max(8, min(w, h) / 12)
        // integral image box blur
        val integ = LongArray((w + 1) * (h + 1))
        for (y in 0 until h) {
            var row = 0L
            for (x in 0 until w) {
                row += luma[y * w + x]
                integ[(y + 1) * (w + 1) + x + 1] = integ[y * (w + 1) + x + 1] + row
            }
        }
        val out = IntArray(w * h)
        for (y in 0 until h) {
            val y0 = max(0, y - r); val y1 = min(h, y + r + 1)
            for (x in 0 until w) {
                val x0 = max(0, x - r); val x1 = min(w, x + r + 1)
                val s = integ[y1 * (w + 1) + x1] - integ[y0 * (w + 1) + x1] - integ[y1 * (w + 1) + x0] + integ[y0 * (w + 1) + x0]
                val mean = s.toDouble() / ((x1 - x0) * (y1 - y0))
                // paper is brighter than the local mean near ink; scale mean up a little
                val bg = max(1.0, min(255.0, mean * 1.08))
                out[y * w + x] = min(255, (luma[y * w + x] * 255.0 / bg).toInt())
            }
        }
        return out
    }

    /**
     * Converts a signature crop to clean ink on white or transparent.
     * Pixels darker than [threshold] become ink with a smooth anti-aliased edge.
     */
    fun cleanSignature(
        src: Bitmap,
        threshold: Int,
        inkColor: Int,
        transparent: Boolean,
        trim: Boolean,
        normalizedLuma: IntArray? = null,
    ): Bitmap {
        val w = src.width; val h = src.height
        val luma = normalizedLuma ?: normalizeLighting(toLuma(src), w, h)
        val soft = 18
        val out = IntArray(w * h)
        val ir = Color.red(inkColor); val ig = Color.green(inkColor); val ib = Color.blue(inkColor)
        var minX = w; var minY = h; var maxX = -1; var maxY = -1
        for (i in 0 until w * h) {
            val l = luma[i]
            // alpha: 1 when well below threshold, 0 above, linear ramp in between
            val a = ((threshold + soft / 2 - l).toFloat() / soft).coerceIn(0f, 1f)
            if (a > 0.5f) {
                val x = i % w; val y = i / w
                if (x < minX) minX = x; if (x > maxX) maxX = x
                if (y < minY) minY = y; if (y > maxY) maxY = y
            }
            out[i] = if (transparent) {
                Color.argb((a * 255).toInt(), ir, ig, ib)
            } else {
                Color.rgb(
                    (255 + (ir - 255) * a).toInt(), (255 + (ig - 255) * a).toInt(), (255 + (ib - 255) * a).toInt(),
                )
            }
        }
        // remove tiny specks: isolated ink pixels with no ink neighbours
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            if (luma[i] < threshold) {
                var n = 0
                if (luma[i - 1] < threshold) n++
                if (luma[i + 1] < threshold) n++
                if (luma[i - w] < threshold) n++
                if (luma[i + w] < threshold) n++
                if (n == 0) out[i] = if (transparent) Color.TRANSPARENT else Color.WHITE
            }
        }
        val full = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        full.setPixels(out, 0, w, 0, 0, w, h)
        if (!trim || maxX < 0) return full
        val pad = max(4, (max(maxX - minX, maxY - minY) * 0.04f).toInt())
        val rect = Rect(max(0, minX - pad), max(0, minY - pad), min(w, maxX + pad + 1), min(h, maxY + pad + 1))
        val trimmed = Bitmap.createBitmap(full, rect.left, rect.top, rect.width(), rect.height())
        if (trimmed !== full) full.recycle()
        return trimmed
    }

    fun crop(src: Bitmap, left: Float, top: Float, right: Float, bottom: Float): Bitmap {
        val l = (left * src.width).roundToInt().coerceIn(0, src.width - 1)
        val t = (top * src.height).roundToInt().coerceIn(0, src.height - 1)
        val r = (right * src.width).roundToInt().coerceIn(l + 1, src.width)
        val b = (bottom * src.height).roundToInt().coerceIn(t + 1, src.height)
        return Bitmap.createBitmap(src, l, t, r - l, b - t)
    }

    @Suppress("unused")
    private fun near(a: Float, b: Float) = abs(a - b) < 0.001f
}
