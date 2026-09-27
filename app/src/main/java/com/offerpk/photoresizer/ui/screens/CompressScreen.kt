package com.offerpk.photoresizer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.offerpk.photoresizer.AppViewModel
import com.offerpk.photoresizer.ExportResult
import com.offerpk.photoresizer.core.KbCompressor
import com.offerpk.photoresizer.core.Units
import com.offerpk.photoresizer.image.ImageOps
import com.offerpk.photoresizer.image.SourceImage
import com.offerpk.photoresizer.ui.components.BottomActionBar
import com.offerpk.photoresizer.ui.components.EmptySource
import com.offerpk.photoresizer.ui.components.InfoCard
import com.offerpk.photoresizer.ui.components.LabeledValue
import com.offerpk.photoresizer.ui.components.PhotoPreview
import com.offerpk.photoresizer.ui.components.SectionTitle
import com.offerpk.photoresizer.ui.components.ToolScaffold

val KB_PRESETS = listOf(20, 50, 100, 200, 500)

@Composable
fun OriginalInfo(src: SourceImage) {
    InfoCard {
        LabeledValue("Original size", Units.formatBytes(src.originalBytes))
        LabeledValue("Dimensions", "${src.originalWidth} × ${src.originalHeight} px")
        LabeledValue("Type", src.mimeType.substringAfter('/').uppercase())
    }
}

@Composable
fun ChangePhotoAction(onPick: () -> Unit) {
    IconButton(onClick = onPick) { Icon(Icons.Filled.PhotoLibrary, contentDescription = "Change photo") }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompressScreen(vm: AppViewModel, onBack: () -> Unit, onPick: () -> Unit, onResult: () -> Unit) {
    val src = vm.source
    var preset by rememberSaveable { mutableIntStateOf(50) } // 0 = custom
    var custom by rememberSaveable { mutableStateOf("30") }
    var keepDims by rememberSaveable { mutableStateOf(false) }
    val targetKb = if (preset == 0) custom.toIntOrNull()?.takeIf { it in 1..50_000 } else preset

    ToolScaffold(
        title = "Compress to KB",
        onBack = onBack,
        busy = vm.busy,
        actions = { if (src != null) ChangePhotoAction(onPick) },
        bottomBar = {
            if (src != null) BottomActionBar {
                Button(
                    onClick = {
                        val kb = targetKb ?: return@Button
                        vm.process({ compress(src, kb, keepDims) }, onResult)
                    },
                    enabled = targetKb != null && !vm.busy,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Icon(Icons.Filled.Compress, null); Spacer(Modifier.width(8.dp))
                    Text(if (targetKb != null) "Compress to $targetKb KB" else "Enter a size")
                }
            }
        },
    ) {
        if (src == null) { EmptySource(text = "Pick a photo to compress it to an exact size in KB.", onPick = onPick); return@ToolScaffold }
        PhotoPreview(src.bitmap)
        OriginalInfo(src)
        SectionTitle("Target size (max)")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            KB_PRESETS.forEach { kb ->
                FilterChip(selected = preset == kb, onClick = { preset = kb }, label = { Text("$kb KB") })
            }
            FilterChip(selected = preset == 0, onClick = { preset = 0 }, label = { Text("Custom") })
        }
        if (preset == 0) {
            OutlinedTextField(
                value = custom,
                onValueChange = { v -> custom = v.filter(Char::isDigit).take(5) },
                label = { Text("Max size in KB") },
                suffix = { Text("KB") },
                singleLine = true,
                isError = targetKb == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (targetKb != null && src.originalBytes in 1..Units.kbToBytes(targetKb).toLong()) {
            Text("This photo is already under $targetKb KB – it will be re-saved at the best quality that fits.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
        }
        InfoCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                    Text("Keep original dimensions", style = MaterialTheme.typography.titleSmall)
                    Text("Only lower JPEG quality. Turn off to also shrink the pixel size when needed (sharper result for small targets).",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = keepDims, onCheckedChange = { keepDims = it })
            }
        }
        Text("Output is a JPEG at or under the target. 1 KB = 1000 bytes, the same as your phone's Files app.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
    }
}

internal fun compress(src: SourceImage, targetKb: Int, keepDims: Boolean): ExportResult {
    val r = KbCompressor().compress(ImageOps.BitmapJpegEncoder(src.bitmap), Units.kbToBytes(targetKb), allowScaling = !keepDims)
    return ExportResult(
        bytes = r.bytes,
        mime = "image/jpeg",
        width = r.width,
        height = r.height,
        fileName = "${src.baseName}_${targetKb}kb_${System.currentTimeMillis() % 100000}.jpg",
        title = "Compressed to $targetKb KB",
        beforeBytes = src.originalBytes,
        beforeWidth = src.originalWidth,
        beforeHeight = src.originalHeight,
        details = listOf("JPEG quality ${r.quality}", "Target ≤ $targetKb KB"),
        warning = if (!r.fitsTarget) "Couldn't reach $targetKb KB while keeping the original dimensions. Turn off \"Keep original dimensions\" and try again." else null,
    )
}
