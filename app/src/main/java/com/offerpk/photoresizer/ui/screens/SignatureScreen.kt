package com.offerpk.photoresizer.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import com.offerpk.photoresizer.AppViewModel
import com.offerpk.photoresizer.ExportResult
import com.offerpk.photoresizer.image.ImageOps
import com.offerpk.photoresizer.image.SourceImage
import com.offerpk.photoresizer.ui.components.BottomActionBar
import com.offerpk.photoresizer.ui.components.EmptySource
import com.offerpk.photoresizer.ui.components.FreeCropBox
import com.offerpk.photoresizer.ui.components.FreeCropState
import com.offerpk.photoresizer.ui.components.InfoCard
import com.offerpk.photoresizer.ui.components.PhotoPreview
import com.offerpk.photoresizer.ui.components.SectionTitle
import com.offerpk.photoresizer.ui.components.Segmented
import com.offerpk.photoresizer.ui.components.ToolScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

internal enum class Ink(val label: String, val color: Int) {
    BLACK("Black ink", 0xFF000000.toInt()),
    BLUE("Blue ink", 0xFF0D2A8A.toInt()),
}
internal enum class Bg(val label: String) { WHITE("White"), TRANSPARENT("Transparent") }
private val SIG_WIDTHS = listOf(0, 800, 600, 400, 300)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SignatureScreen(vm: AppViewModel, onBack: () -> Unit, onPick: () -> Unit, onResult: () -> Unit) {
    val src = vm.source
    val crop = remember(src) { FreeCropState() }
    var cropVersion by remember { mutableIntStateOf(0) }
    var threshold by rememberSaveable(src) { mutableFloatStateOf(-1f) } // -1 = auto (not computed yet)
    var ink by rememberSaveable { mutableStateOf(Ink.BLACK) }
    var bg by rememberSaveable { mutableStateOf(Bg.TRANSPARENT) }
    var trim by rememberSaveable { mutableStateOf(true) }
    var widthPx by rememberSaveable { mutableIntStateOf(0) }

    // Downscaled working copy for a fast live preview
    val small = remember(src) { src?.bitmap?.let { b ->
        val s = 1000f / max(b.width, b.height)
        if (s < 1f) ImageOps.scale(b, (b.width * s).roundToInt(), (b.height * s).roundToInt()) else b
    } }
    var lumaCache by remember(src) { mutableStateOf<Pair<Rect, Pair<Bitmap, IntArray>>?>(null) }
    var preview by remember(src) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(small, cropVersion, threshold, ink, bg, trim) {
        val s = small ?: return@LaunchedEffect
        delay(60)
        val r = crop.rect
        val (c, luma) = withContext(Dispatchers.Default) {
            lumaCache?.takeIf { it.first == r }?.second ?: run {
                val c = ImageOps.crop(s, r.left, r.top, r.right, r.bottom)
                val l = ImageOps.normalizeLighting(ImageOps.toLuma(c), c.width, c.height)
                c to l
            }
        }
        lumaCache = r to (c to luma)
        if (threshold < 0f) { threshold = ImageOps.otsuThreshold(luma).coerceIn(60, 230).toFloat(); return@LaunchedEffect }
        preview = withContext(Dispatchers.Default) {
            ImageOps.cleanSignature(c, threshold.toInt(), ink.color, bg == Bg.TRANSPARENT, trim, luma)
        }
    }

    ToolScaffold(
        title = "Signature cleaner",
        onBack = onBack,
        busy = vm.busy,
        actions = { if (src != null) ChangePhotoAction(onPick) },
        bottomBar = {
            if (src != null) BottomActionBar {
                Button(
                    onClick = {
                        val r = crop.rect
                        vm.process({ exportSignature(src, r, threshold.toInt().coerceAtLeast(1), ink, bg, trim, widthPx) }, onResult)
                    },
                    enabled = threshold >= 0f && !vm.busy,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Icon(Icons.Filled.Draw, null); Spacer(Modifier.width(8.dp)); Text("Export PNG") }
            }
        },
    ) {
        if (src == null) { EmptySource(text = "Sign on plain white paper, take a photo, then pick it here.", onPick = onPick); return@ToolScaffold }
        FreeCropBox(src.bitmap, crop, height = 250.dp, onDragEnd = { cropVersion++ })
        Text("Drag the corners to crop around your signature.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        SectionTitle("Result")
        preview?.let { PhotoPreview(it, height = 150.dp, checker = bg == Bg.TRANSPARENT) }
        InfoCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ink threshold ${threshold.toInt().coerceAtLeast(0)}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { threshold = -1f }) { Text("Auto") }
            }
            Slider(value = threshold.coerceAtLeast(0f), onValueChange = { threshold = it }, valueRange = 20f..250f)
            Text("Higher keeps lighter strokes; lower removes more shadows and paper texture.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Segmented(Ink.entries, ink, { it.label }, { ink = it })
        Segmented(Bg.entries, bg, { it.label }, { bg = it })
        InfoCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Trim empty space", style = MaterialTheme.typography.titleSmall)
                    Text("Crops tightly around the ink with a small margin.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = trim, onCheckedChange = { trim = it })
            }
        }
        SectionTitle("Output width")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SIG_WIDTHS.forEach { w -> FilterChip(selected = widthPx == w, onClick = { widthPx = w }, label = { Text(if (w == 0) "Original" else "$w px") }) }
        }
        Spacer(Modifier.height(8.dp))
    }
}

internal fun exportSignature(src: SourceImage, r: Rect, threshold: Int, ink: Ink, bg: Bg, trim: Boolean, widthPx: Int): ExportResult {
    val c = ImageOps.crop(src.bitmap, r.left, r.top, r.right, r.bottom)
    var out = ImageOps.cleanSignature(c, threshold, ink.color, bg == Bg.TRANSPARENT, trim)
    if (widthPx in 1 until out.width) {
        out = ImageOps.scale(out, widthPx, max(1, (out.height * widthPx.toFloat() / out.width).roundToInt()))
    }
    val bytes = ImageOps.png(out)
    return ExportResult(
        bytes = bytes,
        mime = "image/png",
        width = out.width, height = out.height,
        fileName = "signature_${System.currentTimeMillis() % 100000}.png",
        title = "Clean signature",
        beforeBytes = src.originalBytes, beforeWidth = src.originalWidth, beforeHeight = src.originalHeight,
        details = listOf("PNG · ${ink.label.lowercase()}", if (bg == Bg.TRANSPARENT) "Transparent background" else "White background"),
        transparent = bg == Bg.TRANSPARENT,
        showSavings = false,
    )
}
