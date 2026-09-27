package com.offerpk.photoresizer

import com.offerpk.photoresizer.core.JpegEncoder
import com.offerpk.photoresizer.core.KbCompressor
import com.offerpk.photoresizer.core.Units
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.random.Random

/** Real JPEG encoder (javax.imageio) so the search runs against genuine JPEG sizes. */
private class AwtJpegEncoder(private val src: BufferedImage) : JpegEncoder {
    override val sourceWidth get() = src.width
    override val sourceHeight get() = src.height

    override fun encode(width: Int, height: Int, quality: Int): ByteArray {
        val img = if (width == src.width && height == src.height) src else {
            BufferedImage(width, height, BufferedImage.TYPE_INT_RGB).also { dst ->
                val g = dst.createGraphics()
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                g.drawImage(src, 0, 0, width, height, null)
                g.dispose()
            }
        }
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        val out = ByteArrayOutputStream()
        ImageIO.createImageOutputStream(out).use { ios ->
            writer.output = ios
            val p = writer.defaultWriteParam.apply {
                compressionMode = ImageWriteParam.MODE_EXPLICIT
                compressionQuality = quality / 100f
            }
            writer.write(null, IIOImage(img, null, null), p)
        }
        writer.dispose()
        return out.toByteArray()
    }
}

/** Photo-like synthetic image: smooth gradients, shapes and sensor-like noise. */
private fun syntheticPhoto(w: Int, h: Int, seed: Int): BufferedImage {
    val rnd = Random(seed)
    val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
    val g = img.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.paint = java.awt.GradientPaint(0f, 0f, Color(90, 150, 220), w.toFloat(), h.toFloat(), Color(250, 210, 160))
    g.fillRect(0, 0, w, h)
    repeat(60) {
        g.color = Color(rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256), 140)
        val s = rnd.nextInt(w / 12, w / 3)
        g.fillOval(rnd.nextInt(w), rnd.nextInt(h), s, (s * rnd.nextDouble(0.5, 1.5)).toInt())
    }
    g.dispose()
    for (y in 0 until h) for (x in 0 until w) {
        val c = img.getRGB(x, y)
        val n = rnd.nextInt(-14, 15)
        val r = ((c shr 16 and 0xFF) + n).coerceIn(0, 255)
        val gg = ((c shr 8 and 0xFF) + n).coerceIn(0, 255)
        val b = ((c and 0xFF) + n).coerceIn(0, 255)
        img.setRGB(x, y, (r shl 16) or (gg shl 8) or b)
    }
    return img
}

class KbCompressorTest {
    private val compressor = KbCompressor()
    private val big = AwtJpegEncoder(syntheticPhoto(2400, 1800, seed = 7))
    private val medium = AwtJpegEncoder(syntheticPhoto(1000, 1300, seed = 11))

    private fun assertHits(enc: JpegEncoder, targetKb: Int, allowScaling: Boolean = true) {
        val target = Units.kbToBytes(targetKb)
        val r = compressor.compress(enc, target, allowScaling)
        val ratio = r.sizeBytes.toDouble() / target
        println("target=${targetKb}KB -> ${r.sizeBytes / 1000.0}KB (${"%.1f".format(ratio * 100)}%) " +
            "q=${r.quality} ${r.width}x${r.height} calls=${r.encodeCalls}")
        assertTrue("must not exceed target: ${r.sizeBytes} > $target", r.sizeBytes <= target)
        assertTrue(r.fitsTarget)
        // "±5% or under": never over the target and within 5% of it.
        assertTrue("too far under target: ${"%.1f".format(ratio * 100)}%", ratio >= 0.95)
        // aspect ratio is preserved when scaling
        val srcAspect = enc.sourceWidth.toDouble() / enc.sourceHeight
        assertEquals(srcAspect, r.width.toDouble() / r.height, 0.02)
        assertTrue(r.encodeCalls < 40)
    }

    @Test fun hits20Kb() = assertHits(big, 20)
    @Test fun hits50Kb() = assertHits(big, 50)
    @Test fun hits100Kb() = assertHits(big, 100)
    @Test fun hits200Kb() = assertHits(big, 200)
    @Test fun hits50KbPortrait() = assertHits(medium, 50)
    @Test fun hits100KbPortrait() = assertHits(medium, 100)

    @Test fun smallImageUnderTargetIsReturnedAtMaxQuality() {
        val tiny = AwtJpegEncoder(syntheticPhoto(120, 90, seed = 3))
        val r = compressor.compress(tiny, 500_000)
        assertEquals(95, r.quality)
        assertEquals(120, r.width)
        assertTrue(r.fitsTarget)
    }

    @Test fun keepDimensionsOnlyChangesQuality() {
        val r = compressor.compress(medium, 150_000, allowScaling = false)
        println("keepDims 150KB -> ${r.sizeBytes / 1000.0}KB q=${r.quality}")
        assertEquals(1000, r.width)
        assertEquals(1300, r.height)
        assertTrue(r.sizeBytes <= 150_000)
    }

    @Test fun impossibleTargetWithoutScalingReportsNoFit() {
        val r = compressor.compress(big, 3_000, allowScaling = false)
        assertFalse(r.fitsTarget)
        assertEquals(2400, r.width)
    }

    @Test fun veryTinyTargetScalesDown() {
        val target = 5_000
        val r = compressor.compress(big, target)
        assertTrue(r.sizeBytes <= target)
        assertTrue(r.width < 2400)
    }
}
