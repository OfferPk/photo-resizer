package com.offerpk.photoresizer.core

import java.util.Locale
import kotlin.math.roundToInt

object Units {
    const val MM_PER_INCH = 25.4

    fun mmToPx(mm: Double, dpi: Int): Int = (mm / MM_PER_INCH * dpi).roundToInt()

    /**
     * 1 KB = 1000 bytes – the same convention Android's Files app and most phones use,
     * so a "50 KB" result never shows up as "51 KB" in the gallery or a file manager.
     * (A 50,000-byte file is also under 50 KB on systems that count 1 KB = 1024 bytes.)
     */
    const val BYTES_PER_KB = 1000

    fun kbToBytes(kb: Int): Int = kb * BYTES_PER_KB

    fun formatBytes(bytes: Long): String = when {
        bytes < BYTES_PER_KB -> "$bytes B"
        bytes < BYTES_PER_KB * BYTES_PER_KB -> String.format(Locale.US, "%.1f KB", bytes / 1000.0)
        else -> String.format(Locale.US, "%.2f MB", bytes / 1_000_000.0)
    }

    /** Percentage saved going from [before] to [after] bytes (negative if bigger). */
    fun savedPercent(before: Long, after: Long): Int =
        if (before <= 0) 0 else ((before - after) * 100.0 / before).roundToInt()
}

/**
 * Tiles copies of a photo onto a sheet. Returns top-left positions (px) for each copy.
 * Leftover space is spread evenly as gaps/margins so copies are easy to cut out.
 */
object SheetLayout {
    data class Placement(val x: Int, val y: Int)

    fun layout(sheetW: Int, sheetH: Int, photoW: Int, photoH: Int, minGap: Int = 0): List<Placement> {
        if (photoW <= 0 || photoH <= 0 || photoW > sheetW || photoH > sheetH) return emptyList()
        fun count(total: Int, item: Int): Int {
            var n = total / item
            while (n > 1 && n * item + (n + 1) * minGap > total) n--
            return n
        }
        val cols = count(sheetW, photoW)
        val rows = count(sheetH, photoH)
        val gapX = (sheetW - cols * photoW) / (cols + 1)
        val gapY = (sheetH - rows * photoH) / (rows + 1)
        val out = ArrayList<Placement>(cols * rows)
        for (r in 0 until rows) for (c in 0 until cols) {
            out += Placement(gapX + c * (photoW + gapX), gapY + r * (photoH + gapY))
        }
        return out
    }
}
