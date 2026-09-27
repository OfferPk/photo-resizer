package com.offerpk.photoresizer.ui.screens

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Portrait
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.offerpk.photoresizer.AppViewModel
import com.offerpk.photoresizer.ExportResult
import com.offerpk.photoresizer.core.KbCompressor
import com.offerpk.photoresizer.core.Units
import com.offerpk.photoresizer.image.ImageOps
import com.offerpk.photoresizer.image.SourceImage
import com.offerpk.photoresizer.ui.components.AspectCropState
import com.offerpk.photoresizer.ui.components.AspectCropper
import com.offerpk.photoresizer.ui.components.BottomActionBar
import com.offerpk.photoresizer.ui.components.EmptySource
import com.offerpk.photoresizer.ui.components.InfoCard
import com.offerpk.photoresizer.ui.components.SectionTitle
import com.offerpk.photoresizer.ui.components.ToolScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import java.util.Locale

const val PRINT_DPI = 300

private data class PhotoSize(val label: String, val wMm: Double, val hMm: Double)
private val PHOTO_SIZES = listOf(
    PhotoSize("35×45 mm", 35.0, 45.0),
    PhotoSize("2×2 in", 50.8, 50.8),
    PhotoSize("30×40 mm", 30.0, 40.0),
    PhotoSize("40×50 mm", 40.0, 50.0),
    PhotoSize("33×48 mm", 33.0, 48.0),
)
private val PASSPORT_KB = listOf(0, 50, 100, 200, 300)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PassportScreen(vm: AppViewModel, onBack: () -> Unit, onPick: () -> Unit, onResult: () -> Unit) {
    val src = vm.source
    var sizeIdx by rememberSaveable { mutableIntStateOf(0) } // -1 = custom
    var customW by rememberSaveable { mutableStateOf("35") }
    var customH by rememberSaveable { mutableStateOf("45") }
    var whiten by rememberSaveable { mutableStateOf(false) }
    var tolerance by rememberSaveable { mutableFloatStateOf(40f) }
    var maxKb by rememberSaveable { mutableIntStateOf(0) }

    val (wMm, hMm) = if (sizeIdx >= 0) PHOTO_SIZES[sizeIdx].let { it.wMm to it.hMm } else
        (customW.toDoubleOrNull() ?: 0.0) to (customH.toDoubleOrNull() ?: 0.0)
    val validSize = wMm in 10.0..150.0 && hMm in 10.0..150.0
    val outW = if (validSize) Units.mmToPx(wMm, PRINT_DPI) else 0
    val outH = if (validSize) Units.mmToPx(hMm, PRINT_DPI) else 0
    val aspect = if (validSize) (wMm / hMm).toFloat() else 35f / 45f
    val cropState = remember(src, aspect) { src?.let { AspectCropState(it.bitmap.width, it.bitmap.height, aspect) } }

    ToolScaffold(
        title = "Passport / ID photo",
        onBack = onBack,
        busy = vm.busy,
        actions = { if (src != null) ChangePhotoAction(onPick) },
        bottomBar = {
            if (src != null) BottomActionBar {
                OutlinedButton(
                    onClick = {
                        val rect = cropState?.cropRect() ?: return@OutlinedButton
                        vm.process({ sheet(src, rect, outW, outH, whiten, tolerance.toInt(), wMm, hMm) }, onResult)
                    },
                    enabled = validSize && !vm.busy,
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Icon(Icons.Filled.GridView, null); Spacer(Modifier.width(6.dp)); Text("4×6 sheet") }
                Button(
                    onClick = {
                        val rect = cropState?.cropRect() ?: return@Button
                        vm.process({ single(src, rect, outW, outH, whiten, tolerance.toInt(), maxKb, wMm, hMm) }, onResult)
                    },
                    enabled = validSize && !vm.busy,
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Icon(Icons.Filled.Portrait, null); Spacer(Modifier.width(6.dp)); Text("Photo") }
            }
        },
    ) {
        if (src == null || cropState == null) { EmptySource(text = "Pick a clear, front-facing photo taken against a plain wall.", onPick = onPick); return@ToolScaffold }

        SectionTitle("Photo size")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            PHOTO_SIZES.forEachIndexed { i, s -> FilterChip(selected = sizeIdx == i, onClick = { sizeIdx = i }, label = { Text(s.label) }) }
            FilterChip(selected = sizeIdx == -1, onClick = { sizeIdx = -1 }, label = { Text("Custom") })
        }
        if (sizeIdx == -1) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(customW, { customW = it.filter { c -> c.isDigit() || c == '.' }.take(5) }, label = { Text("Width") },
                    suffix = { Text("mm") }, singleLine = true, isError = !validSize,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                OutlinedTextField(customH, { customH = it.filter { c -> c.isDigit() || c == '.' }.take(5) }, label = { Text("Height") },
                    suffix = { Text("mm") }, singleLine = true, isError = !validSize,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
            }
        }
        if (validSize) {
            Text("${fmt(wMm)}×${fmt(hMm)} mm → $outW × $outH px at $PRINT_DPI DPI",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        val state = cropState
        AspectCropper(src.bitmap, state, maxHeight = 340.dp)
        Text("Pinch to zoom and drag to position. Keep the face inside the oval, eyes level.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        var preview by remember { mutableStateOf<Bitmap?>(null) }
        LaunchedEffect(state, whiten, tolerance, outW, outH) {
            snapshotFlow { state.cropRect() }.collectLatest { r ->
                delay(180)
                if (outW <= 0) return@collectLatest
                preview = withContext(Dispatchers.Default) {
                    val pw = outW / 2; val ph = outH / 2
                    val b = ImageOps.cropTo(src.bitmap, r, pw, ph)
                    if (whiten) ImageOps.whitenBackground(b, tolerance.toInt()) else b
                }
            }
        }
        InfoCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("White background", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Switch(checked = whiten, onCheckedChange = { whiten = it })
                    }
                    Text("Brightens a plain, light wall to pure white. Works best with an even background.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (whiten) {
                        Text("Strength ${tolerance.toInt()}", style = MaterialTheme.typography.bodySmall)
                        Slider(value = tolerance, onValueChange = { tolerance = it }, valueRange = 0f..100f)
                    }
                }
                Spacer(Modifier.width(12.dp))
                preview?.let {
                    Image(it.asImageBitmap(), "Result preview", Modifier.width(76.dp).border(1.dp, Color(0xFFBBBBBB)))
                }
            }
        }
        SectionTitle("Max file size (optional)")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PASSPORT_KB.forEach { kb ->
                FilterChip(selected = maxKb == kb, onClick = { maxKb = kb }, label = { Text(if (kb == 0) "No limit" else "$kb KB") })
            }
        }
        Text("Always check the exact photo rules of the place you're applying to. The 4×6 in sheet fits standard photo-lab prints.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
    }
}

private fun fmt(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else String.format(Locale.US, "%.1f", v)

internal fun render(src: SourceImage, rect: RectF, w: Int, h: Int, whiten: Boolean, tol: Int): Bitmap {
    val b = ImageOps.cropTo(src.bitmap, rect, w, h)
    return if (whiten) ImageOps.whitenBackground(b, tol) else b
}

internal fun single(src: SourceImage, rect: RectF, w: Int, h: Int, whiten: Boolean, tol: Int, maxKb: Int, wMm: Double, hMm: Double): ExportResult {
    val bmp = render(src, rect, w, h, whiten, tol)
    var warning: String? = null
    var quality = 95
    val bytes = if (maxKb > 0) {
        val r = KbCompressor().compress(ImageOps.BitmapJpegEncoder(bmp), Units.kbToBytes(maxKb), allowScaling = false)
        if (!r.fitsTarget) warning = "Couldn't get under $maxKb KB at this size."
        quality = r.quality
        r.bytes
    } else ImageOps.jpeg(bmp, 95)
    return ExportResult(
        bytes = ImageOps.withJpegDpi(bytes, PRINT_DPI),
        mime = "image/jpeg",
        width = w, height = h,
        fileName = "passport_${fmt(wMm)}x${fmt(hMm)}mm_${System.currentTimeMillis() % 100000}.jpg",
        title = "${fmt(wMm)}×${fmt(hMm)} mm photo",
        beforeBytes = src.originalBytes, beforeWidth = src.originalWidth, beforeHeight = src.originalHeight,
        details = listOfNotNull("$PRINT_DPI DPI · JPEG quality $quality", if (whiten) "White background" else null,
            if (maxKb > 0) "Max $maxKb KB" else null),
        warning = warning,
    )
}

internal fun sheet(src: SourceImage, rect: RectF, w: Int, h: Int, whiten: Boolean, tol: Int, wMm: Double, hMm: Double): ExportResult {
    val photo = render(src, rect, w, h, whiten, tol)
    val (sheet, count) = ImageOps.printSheet(photo)
    val bytes = ImageOps.withJpegDpi(ImageOps.jpeg(sheet, 95), PRINT_DPI)
    return ExportResult(
        bytes = bytes,
        mime = "image/jpeg",
        width = sheet.width, height = sheet.height,
        fileName = "print_sheet_4x6_${fmt(wMm)}x${fmt(hMm)}mm_${System.currentTimeMillis() % 100000}.jpg",
        title = "4×6 in print sheet · $count copies",
        beforeBytes = src.originalBytes, beforeWidth = src.originalWidth, beforeHeight = src.originalHeight,
        details = listOf("$count × ${fmt(wMm)}×${fmt(hMm)} mm photos", "6×4 in at $PRINT_DPI DPI – print at 100% / actual size"),
        warning = if (count == 0) "This photo size doesn't fit on a 4×6 sheet." else null,
        showSavings = false,
    )
}
