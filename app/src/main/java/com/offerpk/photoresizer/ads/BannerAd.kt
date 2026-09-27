package com.offerpk.photoresizer.ads

import android.view.ViewGroup
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

/** Anchored adaptive banner. Renders nothing until consent + SDK init are done. */
@Composable
fun BannerAd(modifier: Modifier = Modifier) {
    val canShow by AdsManager.canShowAds.collectAsState()
    if (!canShow) return
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val context = LocalContext.current
        val widthDp = maxWidth.value.toInt().coerceAtLeast(320)
        val adSize = remember(widthDp) {
            AdSize.getLargeAnchoredAdaptiveBannerAdSize(context, widthDp)
        }
        val adView = remember(adSize) {
            AdView(context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setAdSize(adSize)
                adUnitId = AdConfig.BANNER_ID
                loadAd(AdRequest.Builder().build())
            }
        }
        DisposableEffect(adView) { onDispose { adView.destroy() } }
        AndroidView(
            factory = { adView },
            modifier = Modifier.fillMaxWidth().height(adSize.height.dp),
        )
    }
}
