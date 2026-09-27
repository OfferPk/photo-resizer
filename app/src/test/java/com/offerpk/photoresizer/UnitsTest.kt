package com.offerpk.photoresizer

import com.offerpk.photoresizer.core.SheetLayout
import com.offerpk.photoresizer.core.Units
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnitsTest {
    @Test fun passportSizesAt300Dpi() {
        assertEquals(413, Units.mmToPx(35.0, 300))
        assertEquals(531, Units.mmToPx(45.0, 300))
        assertEquals(600, Units.mmToPx(50.8, 300)) // 2 inch
    }

    @Test fun formatBytes() {
        assertEquals("512 B", Units.formatBytes(512))
        assertEquals("48.7 KB", Units.formatBytes(48_700))
        assertEquals("2.00 MB", Units.formatBytes(2_000_000))
        assertEquals(50_000, Units.kbToBytes(50))
    }

    @Test fun sheet4x6FitsEightPassportPhotos() {
        val p = SheetLayout.layout(1800, 1200, 413, 531, minGap = 12)
        assertEquals(8, p.size)
        p.forEach { assertTrue(it.x >= 0 && it.x + 413 <= 1800 && it.y + 531 <= 1200) }
    }

    @Test fun sheet4x6FitsSix2x2Photos() {
        assertEquals(6, SheetLayout.layout(1800, 1200, 600, 600).size)
    }

    @Test fun photoLargerThanSheetGivesNothing() {
        assertEquals(0, SheetLayout.layout(1000, 1000, 1200, 500).size)
    }
}
