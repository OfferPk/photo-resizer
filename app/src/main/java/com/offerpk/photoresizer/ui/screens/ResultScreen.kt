package com.offerpk.photoresizer.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.offerpk.photoresizer.AppViewModel
import com.offerpk.photoresizer.ExportResult
import com.offerpk.photoresizer.ads.AdsManager
import com.offerpk.photoresizer.core.Units
import com.offerpk.photoresizer.image.ImageSaver
import com.offerpk.photoresizer.ui.components.BottomActionBar
import com.offerpk.photoresizer.ui.components.InfoCard
import com.offerpk.photoresizer.ui.components.PhotoPreview
import com.offerpk.photoresizer.ui.components.ToolScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ResultScreen(vm: AppViewModel, onBack: () -> Unit, onDone: () -> Unit) {
    val result = vm.result
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var savedMsg by remember { mutableStateOf<String?>(null) }
    BackHandler(onBack = onBack)

    fun doSave(r: ExportResult) {
        saving = true
        scope.launch {
            try {
                val uri = withContext(Dispatchers.IO) { ImageSaver.saveToGallery(context, r.bytes, r.mime, r.fileName) }
                vm.savedUri = uri
                vm.exported = true
                AdsManager.onExportSucceeded()
                savedMsg = "Saved to Pictures/${ImageSaver.ALBUM}"
            } catch (t: Throwable) {
                vm.error = "Couldn't save: ${t.message ?: "unknown error"}"
            } finally { saving = false }
        }
    }
    val legacyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val r = vm.result
        if (granted && r != null) doSave(r) else if (!granted) vm.error = "Storage permission is needed to save on this Android version. You can still use Share."
    }

    ToolScaffold(
        title = "Result",
        onBack = onBack,
        busy = saving,
        actions = { TextButton(onClick = onDone) { Text("Done") } },
        bottomBar = {
            if (result != null) BottomActionBar {
                OutlinedButton(
                    onClick = {
                        ImageSaver.share(context, result.bytes, result.mime, result.fileName)
                        vm.exported = true
                        AdsManager.onExportSucceeded()
                    },
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Icon(Icons.Filled.Share, null); Spacer(Modifier.width(8.dp)); Text("Share") }
                Button(
                    onClick = {
                        if (ImageSaver.needsLegacyPermission &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                        ) legacyPermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        else doSave(result)
                    },
                    enabled = vm.savedUri == null && !saving,
                    modifier = Modifier.weight(1f).height(52.dp),
                ) {
                    Icon(if (vm.savedUri == null) Icons.Filled.Download else Icons.Filled.CheckCircle, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (vm.savedUri == null) "Save" else "Saved")
                }
            }
        },
    ) {
        if (result == null) { Text("Nothing to show yet."); return@ToolScaffold }
        Text(result.title, style = MaterialTheme.typography.headlineSmall)
        result.preview?.let { PhotoPreview(it, height = 300.dp, checker = result.transparent) }
        BeforeAfter(result)
        result.warning?.let { w ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.errorContainer).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(Modifier.width(8.dp))
                Text(w, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (result.details.isNotEmpty()) InfoCard { result.details.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) } }
        savedMsg?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(it, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun BeforeAfter(r: ExportResult) {
    InfoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Before", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(r.beforeBytes?.let { Units.formatBytes(it) } ?: "–", style = MaterialTheme.typography.titleLarge)
                Text(if (r.beforeWidth != null) "${r.beforeWidth} × ${r.beforeHeight} px" else "", style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("After", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Units.formatBytes(r.bytes.size.toLong()), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Text("${r.width} × ${r.height} px", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (r.showSavings) r.beforeBytes?.let { before ->
            val pct = Units.savedPercent(before, r.bytes.size.toLong())
            if (pct > 0) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "$pct% smaller", color = MaterialTheme.colorScheme.onTertiaryContainer,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.tertiaryContainer).padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }
    }
}
