package com.offerpk.photoresizer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.offerpk.photoresizer.ads.AdsManager
import com.offerpk.photoresizer.ui.AppNavigation
import com.offerpk.photoresizer.ui.theme.PhotoResizerTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Consent first; ads are only requested after UMP says we can.
        AdsManager.gatherConsentAndInit(this)
        setContent {
            PhotoResizerTheme {
                AppNavigation(vm)
            }
        }
    }
}
