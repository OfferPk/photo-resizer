package com.offerpk.photoresizer.core

/**
 * Decides when an interstitial may be shown. Rules:
 *  - only after a successful export (save to gallery or share),
 *  - never on the first export of an app session,
 *  - at most once per [minIntervalMs] (default 90 s).
 */
class AdGate(
    private val minIntervalMs: Long = 90_000L,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private var exportsThisSession = 0
    private var lastShownAt: Long? = null
    private var pendingExport = false

    /** Call after every successful export. */
    fun onExportSucceeded() {
        exportsThisSession++
        pendingExport = true
    }

    /**
     * Call at the natural break after an export (leaving the result screen).
     * Returns true when an interstitial may be shown right now. Consumes the
     * pending export either way, so an ad is never shown without a fresh export.
     */
    fun consumeShouldShow(): Boolean {
        if (!pendingExport) return false
        pendingExport = false
        if (exportsThisSession < 2) return false
        val last = lastShownAt
        return last == null || clock() - last >= minIntervalMs
    }

    fun onInterstitialShown() {
        lastShownAt = clock()
    }
}
