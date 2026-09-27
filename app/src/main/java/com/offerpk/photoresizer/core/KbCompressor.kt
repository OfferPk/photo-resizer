package com.offerpk.photoresizer.core

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Abstraction over a JPEG encoder so the search logic is pure Kotlin and can be
 * unit-tested on the JVM (tests use javax.imageio, the app uses Bitmap.compress).
 */
interface JpegEncoder {
    val sourceWidth: Int
    val sourceHeight: Int

    /** Encodes the source scaled to [width]x[height] at JPEG [quality] (1..100). */
    fun encode(width: Int, height: Int, quality: Int): ByteArray
}

data class CompressionResult(
    val bytes: ByteArray,
    val quality: Int,
    val width: Int,
    val height: Int,
    /** True when the output is at or below the requested target. */
    val fitsTarget: Boolean,
    val encodeCalls: Int,
) {
    val sizeBytes: Int get() = bytes.size
}

/**
 * Compresses an image to the largest JPEG that is <= a target size in bytes.
 *
 * Strategy (keeps as much visual quality as possible):
 *  1. Full resolution at [maxQuality]. If that already fits, done.
 *  2. Full resolution, binary search the quality in [preferredMinQuality, maxQuality].
 *  3. Otherwise the image has too many pixels for the budget: binary search the
 *     largest scale that fits at [scaleQuality], then binary search the quality
 *     again at that scale to use up the remaining budget.
 *  4. If even the smallest allowed size does not fit, drop quality down to
 *     [minQuality] as a last resort.
 *
 * With `allowScaling = false` the pixel dimensions are never changed (useful when a
 * form requires exact dimensions, e.g. passport photos) and only quality is searched
 * down to [minQuality].
 */
class KbCompressor(
    private val minQuality: Int = 10,
    private val maxQuality: Int = 95,
    private val preferredMinQuality: Int = 55,
    private val scaleQuality: Int = 80,
    private val minDimension: Int = 16,
) {
    init {
        require(minQuality in 1..maxQuality && maxQuality <= 100)
        require(preferredMinQuality in minQuality..maxQuality)
        require(scaleQuality in minQuality..maxQuality)
    }

    fun compress(encoder: JpegEncoder, targetBytes: Int, allowScaling: Boolean = true): CompressionResult {
        require(targetBytes > 0) { "targetBytes must be > 0" }
        val w0 = encoder.sourceWidth
        val h0 = encoder.sourceHeight
        require(w0 > 0 && h0 > 0)

        var calls = 0
        val cache = HashMap<Triple<Int, Int, Int>, ByteArray>()
        fun enc(w: Int, h: Int, q: Int): ByteArray =
            cache.getOrPut(Triple(w, h, q)) { calls++; encoder.encode(w, h, q) }

        fun result(b: ByteArray, q: Int, w: Int, h: Int) =
            CompressionResult(b, q, w, h, b.size <= targetBytes, calls)

        /** Largest quality in [lo, hi] that fits, given that [lo] fits. */
        fun searchQuality(w: Int, h: Int, lo: Int, loBytes: ByteArray, hi: Int): CompressionResult {
            val top = enc(w, h, hi)
            if (top.size <= targetBytes) return result(top, hi, w, h)
            var good = lo
            var goodBytes = loBytes
            var bad = hi
            while (bad - good > 1) {
                val mid = (good + bad) ushr 1
                val b = enc(w, h, mid)
                if (b.size <= targetBytes) { good = mid; goodBytes = b } else bad = mid
            }
            return result(goodBytes, good, w, h)
        }

        // 1 + 2: full resolution
        val lowQ = if (allowScaling) preferredMinQuality else minQuality
        val full = enc(w0, h0, maxQuality)
        if (full.size <= targetBytes) return result(full, maxQuality, w0, h0)
        val fullLow = enc(w0, h0, lowQ)
        if (fullLow.size <= targetBytes) return searchQuality(w0, h0, lowQ, fullLow, maxQuality)
        if (!allowScaling) return result(fullLow, lowQ, w0, h0) // cannot fit without resizing

        // 3: find the largest scale that fits at scaleQuality
        fun dims(s: Double): Pair<Int, Int> =
            max(1, (w0 * s).roundToInt()) to max(1, (h0 * s).roundToInt())

        val minScale = min(1.0, minDimension.toDouble() / min(w0, h0))
        val atFull = enc(w0, h0, scaleQuality)
        // bytes scale roughly with pixel count -> sqrt of the ratio is a good first guess
        var s = (sqrt(targetBytes.toDouble() / atFull.size) * 0.97).coerceIn(minScale, 1.0)
        var fitScale = -1.0
        var fitBytes: ByteArray? = null
        var failScale = 1.0
        while (true) {
            val (w, h) = dims(s)
            val b = enc(w, h, scaleQuality)
            if (b.size <= targetBytes) { fitScale = s; fitBytes = b; break }
            failScale = s
            if (s <= minScale) break
            s = max(minScale, s * 0.85)
        }

        if (fitBytes == null) {
            // 4: smallest size still too big – lower the quality as far as allowed
            val (w, h) = dims(minScale)
            val low = enc(w, h, minQuality)
            if (low.size > targetBytes) return result(low, minQuality, w, h)
            return searchQuality(w, h, minQuality, low, scaleQuality)
        }

        // refine the scale between fitScale (fits) and failScale (too big)
        var lo = fitScale
        var hi = failScale
        var loBytes: ByteArray = fitBytes
        repeat(12) {
            val (wl, hl) = dims(lo)
            val (wh, hh) = dims(hi)
            if (wh - wl <= 1 && hh - hl <= 1) return@repeat
            val mid = (lo + hi) / 2
            val (w, h) = dims(mid)
            val b = enc(w, h, scaleQuality)
            if (b.size <= targetBytes) { lo = mid; loBytes = b } else hi = mid
        }
        val (w, h) = dims(lo)
        return searchQuality(w, h, scaleQuality, loBytes, maxQuality)
    }
}
