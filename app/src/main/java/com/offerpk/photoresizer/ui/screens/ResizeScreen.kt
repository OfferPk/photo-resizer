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
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.offerpk.photoresizer.image.ImageOps
import com.offerpk.photoresizer.image.SourceImage
import com.offerpk.photoresizer.ui.components.BottomActionBar
import com.offerpk.photoresizer.ui.components.EmptySource
import com.offerpk.photoresizer.ui.components.InfoCard
import com.offerpk.photoresizer.ui.components.LabeledValue
import com.offerpk.photoresizer.ui.components.PhotoPreview
import com.offerpk.photoresizer.ui.components.SectionTitle
import com.offerpk.photoresizer.ui.components.Segmented
import com.offerpk.photoresizer.ui.components.ToolScaffold
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private enum class ResizeMode(val label: String) { PIXELS("Pixels"), PERCENT("Percent"), PRESET("Presets") }
private enum class Fit(val label: String) { INSIDE("Fit inside"), FILL("Crop to fill") }
private enum class Format(val label: String) { JPEG("JPEG"), PNG("PNG") }

private data class SizePreset(val label: String, val w: Int, val h: Int)
private val SIZE_PRESETS = listOf(
    SizePreset("HD 1280×720", 1280, 720),
    SizePreset("Full HD 1920×1080", 1920, 1080),
    SizePreset("Square 1080×1080", 1080, 1080),
    SizePreset("Portrait 1080×1350", 1080, 1350),
    SizePreset("Story 1080×1920", 1080, 1920),
    SizePreset("640×480", 640, 480),
    SizePreset("Form photo 600×600", 600, 600),
    SizePreset("Small 320×240", 320, 240),
)
private const val MAX_SIDE = 8000
private const val MAX_PIXELS = 40_000_000L

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ResizeScreen(vm: AppViewModel, onBack: () -> Unit, onPick: () -> Unit, onResult: () -> Unit) {
    val src = vm.source
    var mode by rememberSaveable { mutableStateOf(ResizeMode.PIXELS) }
    var wText by rememberSaveable(src) { mutableStateOf(src?.bitmap?.width?.let { (it / 2).toString() } ?: "") }
    var hText by rememberSaveable(src) { mutableStateOf(src?.bitmap?.height?.let { (it / 2).toString() } ?: "") }
    var lock by rememberSaveable { mutableStateOf(true) }
    var percent by rememberSaveable { mutableFloatStateOf(50f) }
    var presetIdx by rememberSaveable { mutableIntStateOf(0) }
    var fit by rememberSaveable { mutableStateOf(Fit.INSIDE) }
    var format by rememberSaveable { mutableStateOf(Format.JPEG) }
    var quality by rememberSaveable { mutableFloatStateOf(90f) }

    val bw = src?.bitmap?.width ?: 1
    val bh = src?.bitmap?.height ?: 1
    val aspect = bw.toFloat() / bh

    // Output dimensions + whether to centre-crop
    val out: Triple<Int, Int, Boolean>? = when (mode) {
        ResizeMode.PIXELS -> {
            val w = wText.toIntOrNull(); val h = hText.toIntOrNull()
            if (w != null && h != null && w > 0 && h > 0) Triple(w, h, false) else null
        }
        ResizeMode.PERCENT -> Triple(max(1, (bw * percent / 100f).roundToInt()), max(1, (bh * percent / 100f).roundToInt()), false)
        ResizeMode.PRESET -> {
            val p = SIZE_PRESETS[presetIdx]
            if (fit == Fit.FILL) Triple(p.w, p.h, true) else {
                val s = min(p.w.toFloat() / bw, p.h.toFloat() / bh)
                Triple(max(1, (bw * s).roundToInt()), max(1, (bh * s).roundToInt()), false)
            }
        }
    }
    val valid = out != null && out.first <= MAX_SIDE && out.second <= MAX_SIDE && out.first.toLong() * out.second <= MAX_PIXELS

    ToolScaffold(
        title = "Resize photo",
        onBack = onBack,
        busy = vm.busy,
        actions = { if (src != null) ChangePhotoAction(onPick) },
        bottomBar = {
            if (src != null) BottomActionBar {
                Button(
                    onClick = {
                        val o = out ?: return@Button
                        vm.process({ resize(src, o.first, o.second, o.third, format == Format.PNG, quality.roundToInt()) }, onResult)
                    },
                    enabled = valid && !vm.busy,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Icon(Icons.Filled.AspectRatio, null); Spacer(Modifier.width(8.dp))
                    Text(if (out != null) "Resize to ${out.first} × ${out.second}" else "Enter a size")
                }
            }
        },
    ) {
        if (src == null) { EmptySource(text = "Pick a photo to resize it by pixels, percentage or a preset.", onPick = onPick); return@ToolScaffold }
        PhotoPreview(src.bitmap, height = 220.dp)
        OriginalInfo(src)
        Segmented(ResizeMode.entries, mode, { it.label }, { mode = it })
        when (mode) {
            ResizeMode.PIXELS -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = wText,
                    onValueChange = { v ->
                        wText = v.filter(Char::isDigit).take(5)
                        if (lock) wText.toIntOrNull()?.let { hText = max(1, (it / aspect).roundToInt()).toString() }
                    },
                    label = { Text("Width") }, suffix = { Text("px") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                IconToggleButton(checked = lock, onCheckedChange = { lock = it }) {
                    Icon(if (lock) Icons.Filled.Link else Icons.Filled.LinkOff, contentDescription = "Keep aspect ratio")
                }
                OutlinedTextField(
                    value = hText,
                    onValueChange = { v ->
                        hText = v.filter(Char::isDigit).take(5)
                        if (lock) hText.toIntOrNull()?.let { wText = max(1, (it * aspect).roundToInt()).toString() }
                    },
                    label = { Text("Height") }, suffix = { Text("px") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            ResizeMode.PERCENT -> {
                Text("${percent.roundToInt()}% of original", style = MaterialTheme.typography.titleSmall)
                Slider(value = percent, onValueChange = { percent = it }, valueRange = 5f..100f)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(25, 50, 75).forEach { p ->
                        FilterChip(selected = percent.roundToInt() == p, onClick = { percent = p.toFloat() }, label = { Text("$p%") })
                    }
                }
            }
            ResizeMode.PRESET -> {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SIZE_PRESETS.forEachIndexed { i, p ->
                        FilterChip(selected = presetIdx == i, onClick = { presetIdx = i }, label = { Text(p.label) })
                    }
                }
                Segmented(Fit.entries, fit, { it.label }, { fit = it })
            }
        }
        SectionTitle("Format")
        Segmented(Format.entries, format, { it.label }, { format = it })
        if (format == Format.JPEG) {
            Text("Quality ${quality.roundToInt()}", style = MaterialTheme.typography.bodyMedium)
            Slider(value = quality, onValueChange = { quality = it }, valueRange = 50f..100f)
        }
        InfoCard {
            LabeledValue("Output size", out?.let { "${it.first} × ${it.second} px" } ?: "–")
            if (out != null && !valid) Text("That is too large (max $MAX_SIDE px per side).", color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(8.dp))
    }
}

internal fun resize(src: SourceImage, w: Int, h: Int, crop: Boolean, png: Boolean, quality: Int): ExportResult {
    val bmp = if (crop) ImageOps.centerCrop(src.bitmap, w, h) else ImageOps.scale(src.bitmap, w, h)
    val bytes = if (png) ImageOps.png(bmp) else ImageOps.jpeg(bmp, quality)
    return ExportResult(
        bytes = bytes,
        mime = if (png) "image/png" else "image/jpeg",
        width = w, height = h,
        fileName = "${src.baseName}_${w}x${h}_${System.currentTimeMillis() % 100000}.${if (png) "png" else "jpg"}",
        title = "Resized to $w × $h",
        beforeBytes = src.originalBytes,
        beforeWidth = src.originalWidth,
        beforeHeight = src.originalHeight,
        details = listOfNotNull(if (png) "PNG (lossless)" else "JPEG quality $quality", if (crop) "Centre-cropped to fill" else null),
    )
}
