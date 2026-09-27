package com.offerpk.photoresizer

import com.offerpk.photoresizer.core.AdGate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdGateTest {
    private var now = 1_000_000L
    private val gate = AdGate(minIntervalMs = 90_000L, clock = { now })

    @Test fun neverWithoutExport() {
        assertFalse(gate.consumeShouldShow())
    }

    @Test fun neverOnFirstExport() {
        gate.onExportSucceeded()
        assertFalse(gate.consumeShouldShow())
    }

    @Test fun secondExportAllowsAd() {
        gate.onExportSucceeded(); gate.consumeShouldShow()
        gate.onExportSucceeded()
        assertTrue(gate.consumeShouldShow())
    }

    @Test fun cappedToOncePer90Seconds() {
        gate.onExportSucceeded(); gate.consumeShouldShow()
        gate.onExportSucceeded(); assertTrue(gate.consumeShouldShow()); gate.onInterstitialShown()
        now += 89_000
        gate.onExportSucceeded(); assertFalse(gate.consumeShouldShow())
        now += 1_000
        gate.onExportSucceeded(); assertTrue(gate.consumeShouldShow())
    }

    @Test fun pendingExportIsConsumed() {
        gate.onExportSucceeded(); gate.consumeShouldShow()
        gate.onExportSucceeded()
        assertTrue(gate.consumeShouldShow())
        assertFalse(gate.consumeShouldShow())
    }
}
