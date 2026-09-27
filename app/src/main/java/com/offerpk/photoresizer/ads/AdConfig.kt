package com.offerpk.photoresizer.ads

import com.offerpk.photoresizer.BuildConfig

/**
 * Ad unit IDs. Values come from /admob.properties at build time (Google TEST IDs by
 * default). Edit that file – not this one – to switch to real IDs.
 */
object AdConfig {
    const val BANNER_ID: String = BuildConfig.ADMOB_BANNER_ID
    const val INTERSTITIAL_ID: String = BuildConfig.ADMOB_INTERSTITIAL_ID
    /** Reserved for a future opt-in reward (not used in 1.0). */
    const val REWARDED_ID: String = BuildConfig.ADMOB_REWARDED_ID

    /** Minimum time between two interstitials. */
    const val INTERSTITIAL_MIN_INTERVAL_MS = 90_000L
}
