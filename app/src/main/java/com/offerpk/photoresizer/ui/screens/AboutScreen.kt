package com.offerpk.photoresizer.ui.screens

import android.content.Intent
import androidx.activity.compose.LocalActivity
import androidx.core.net.toUri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.offerpk.photoresizer.BuildConfig
import com.offerpk.photoresizer.ads.AdsManager
import com.offerpk.photoresizer.image.ImageSaver
import com.offerpk.photoresizer.ui.components.InfoCard
import com.offerpk.photoresizer.ui.components.ToolScaffold

const val PRIVACY_URL = "https://offerpk.github.io/photo-resizer/privacy.html"
const val SOURCE_URL = "https://github.com/OfferPk/photo-resizer"

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val privacyRequired by AdsManager.privacyOptionsRequired.collectAsState()
    fun open(url: String) = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }

    ToolScaffold(title = "About & privacy", onBack = onBack) {
        InfoCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Shield, null, tint = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(12.dp))
                Text("Your photos stay on your phone", style = MaterialTheme.typography.titleMedium)
            }
            Text("All resizing, compression, passport cropping and signature cleaning happens on your device. " +
                "The app has no account, no cloud and never uploads your images. " +
                "The only network use is for ads (Google AdMob).", style = MaterialTheme.typography.bodyMedium)
        }
        InfoCard {
            Item(Icons.Filled.Policy, "Privacy policy", "offerpk.github.io/photo-resizer") { open(PRIVACY_URL) }
            if (privacyRequired) {
                Item(Icons.Filled.PrivacyTip, "Privacy options", "Change your ad consent choices") {
                    activity?.let { AdsManager.showPrivacyOptions(it) }
                }
            }
            Item(Icons.Filled.Folder, "Saved photos", "Pictures/${ImageSaver.ALBUM} in your gallery", null)
            Item(Icons.Filled.Code, "Open source", "github.com/OfferPk/photo-resizer") { open(SOURCE_URL) }
            Item(Icons.Filled.Info, "Version", BuildConfig.VERSION_NAME, null)
        }
        Text("Photo sizes are common presets. Always check the exact requirements of the office, form or website you're applying to. " +
            "This app is not affiliated with any government agency.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Item(icon: ImageVector, title: String, subtitle: String, onClick: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
