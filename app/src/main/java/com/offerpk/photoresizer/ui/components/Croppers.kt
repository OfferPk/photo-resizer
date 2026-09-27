package com.offerpk.photoresizer.ui.components

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pan/zoom state for a fixed-aspect crop window, kept in source-pixel coordinates
 * so the exported crop is exactly what the user sees.
 */
@Stable
class AspectCropState(private val srcW: Int, private val srcH: Int, val aspect: Float) {
    var zoom by mutableFloatStateOf(1f)
        private set
    var cx by mutableFloatStateOf(srcW / 2f)
        private set
    var cy by mutableFloatStateOf(srcH / 2f)
        private set

    private val baseW: Float
    private val baseH: Float

    init {
        val srcAspect = srcW.toFloat() / srcH
        if (srcAspect > aspect) { baseH = srcH.toFloat(); baseW = baseH * aspect }
        else { baseW = srcW.toFloat(); baseH = baseW / aspect }
        // Portraits: start a little above centre where faces usually are
        cy = min(srcH / 2f, max(baseH / 2f, srcH * 0.42f))
        clamp()
    }

    val visibleW get() = baseW / zoom
    val visibleH get() = baseH / zoom

    fun cropRect(): RectF = RectF(cx - visibleW / 2, cy - visibleH / 2, cx + visibleW / 2, cy + visibleH / 2)

    fun transform(panPx: Offset, zoomChange: Float, frameWidthPx: Float) {
        zoom = (zoom * zoomChange).coerceIn(1f, 8f)
        val k = visibleW / frameWidthPx
        cx -= panPx.x * k
        cy -= panPx.y * k
        clamp()
    }

    private fun clamp() {
        cx = cx.coerceIn(visibleW / 2, srcW - visibleW / 2)
        cy = cy.coerceIn(visibleH / 2, srcH - visibleH / 2)
    }
}

@Composable
fun AspectCropper(
    bitmap: Bitmap,
    state: AspectCropState,
    modifier: Modifier = Modifier,
    maxHeight: Dp = 360.dp,
    showFaceGuide: Boolean = true,
) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val density = LocalDensity.current
    val maxWidth = with(density) { (maxHeight.toPx() * state.aspect).toDp() }
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier.widthIn(max = maxWidth).fillMaxWidth().aspectRatio(state.aspect)
                .clip(RoundedCornerShape(8.dp))
                .pointerInput(state) {
                    detectTransformGestures { _, pan, zoom, _ -> state.transform(pan, zoom, size.width.toFloat()) }
                },
        ) {
            val r = state.cropRect()
            drawImage(
                image,
                srcOffset = IntOffset(r.left.roundToInt(), r.top.roundToInt()),
                srcSize = IntSize(r.width().roundToInt().coerceAtLeast(1), r.height().roundToInt().coerceAtLeast(1)),
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                filterQuality = FilterQuality.Medium,
            )
            if (showFaceGuide) {
                val guide = Color.White.copy(alpha = 0.9f)
                val shade = Color.Black.copy(alpha = 0.28f)
                val ovalH = size.height * 0.60f
                val ovalW = min(ovalH * 0.74f, size.width * 0.78f)
                val oval = Rect(Offset((size.width - ovalW) / 2, size.height * 0.44f - ovalH / 2), Size(ovalW, ovalH))
                val p = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(Rect(Offset.Zero, size))
                    addOval(oval)
                }
                drawPath(p, shade)
                drawOval(guide, oval.topLeft, oval.size, style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))))
                drawLine(guide.copy(alpha = 0.5f), Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 1.dp.toPx())
            }
            drawRect(Color.White, style = Stroke(2.dp.toPx()))
        }
    }
}

/** Free-form crop rectangle in normalised (0..1) image coordinates. */
@Stable
class FreeCropState(l: Float = 0.05f, t: Float = 0.2f, r: Float = 0.95f, b: Float = 0.8f) {
    var rect by mutableStateOf(Rect(l, t, r, b))
}

@Composable
fun FreeCropBox(bitmap: Bitmap, state: FreeCropState, modifier: Modifier = Modifier, height: Dp = 280.dp, onDragEnd: () -> Unit = {}) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val primary = MaterialTheme.colorScheme.primary
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(16.dp))) {
        var imgRect by remember { mutableStateOf(Rect.Zero) }
        Canvas(
            Modifier.fillMaxSize().pointerInput(bitmap) {
                var mode = 0 // 0 none, 1 move, 2..5 corners (tl,tr,bl,br)
                val handle = 28.dp.toPx()
                val minN = 0.05f
                detectDragGestures(
                    onDragStart = { p ->
                        val ir = imgRect
                        if (ir.width <= 0f) { mode = 0; return@detectDragGestures }
                        val r = state.rect
                        fun sx(n: Float) = ir.left + n * ir.width
                        fun sy(n: Float) = ir.top + n * ir.height
                        val corners = listOf(
                            Offset(sx(r.left), sy(r.top)), Offset(sx(r.right), sy(r.top)),
                            Offset(sx(r.left), sy(r.bottom)), Offset(sx(r.right), sy(r.bottom)),
                        )
                        val idx = corners.indexOfFirst { abs(it.x - p.x) < handle && abs(it.y - p.y) < handle }
                        mode = when {
                            idx >= 0 -> idx + 2
                            p.x in sx(r.left)..sx(r.right) && p.y in sy(r.top)..sy(r.bottom) -> 1
                            else -> 0
                        }
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                ) { change, drag ->
                    val ir = imgRect
                    if (mode == 0 || ir.width <= 0f) return@detectDragGestures
                    change.consume()
                    val dx = drag.x / ir.width; val dy = drag.y / ir.height
                    val r = state.rect
                    state.rect = when (mode) {
                        1 -> {
                            val mx = dx.coerceIn(-r.left, 1f - r.right)
                            val my = dy.coerceIn(-r.top, 1f - r.bottom)
                            r.translate(mx, my)
                        }
                        2 -> Rect((r.left + dx).coerceIn(0f, r.right - minN), (r.top + dy).coerceIn(0f, r.bottom - minN), r.right, r.bottom)
                        3 -> Rect(r.left, (r.top + dy).coerceIn(0f, r.bottom - minN), (r.right + dx).coerceIn(r.left + minN, 1f), r.bottom)
                        4 -> Rect((r.left + dx).coerceIn(0f, r.right - minN), r.top, r.right, (r.bottom + dy).coerceIn(r.top + minN, 1f))
                        else -> Rect(r.left, r.top, (r.right + dx).coerceIn(r.left + minN, 1f), (r.bottom + dy).coerceIn(r.top + minN, 1f))
                    }
                }
            },
        ) {
            drawRect(Color(0xFF1B1F24))
            val s = min(size.width / bitmap.width, size.height / bitmap.height)
            val w = bitmap.width * s; val h = bitmap.height * s
            val ir = Rect(Offset((size.width - w) / 2, (size.height - h) / 2), Size(w, h))
            imgRect = ir
            drawImage(image, dstOffset = IntOffset(ir.left.roundToInt(), ir.top.roundToInt()),
                dstSize = IntSize(w.roundToInt(), h.roundToInt()), filterQuality = FilterQuality.Medium)
            val r = state.rect
            val cr = Rect(ir.left + r.left * w, ir.top + r.top * h, ir.left + r.right * w, ir.top + r.bottom * h)
            val shade = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(ir); addRect(cr)
            }
            drawPath(shade, Color.Black.copy(alpha = 0.5f))
            drawRect(Color.White, cr.topLeft, cr.size, style = Stroke(2.dp.toPx()))
            val hs = 18.dp.toPx(); val sw = 4.dp.toPx()
            listOf(cr.topLeft, Offset(cr.right, cr.top), Offset(cr.left, cr.bottom), cr.bottomRight).forEach { c ->
                drawRoundRect(primary, Offset(c.x - hs / 2, c.y - hs / 2), Size(hs, hs), CornerRadius(4.dp.toPx()))
                drawRoundRect(Color.White, Offset(c.x - hs / 2, c.y - hs / 2), Size(hs, hs), CornerRadius(4.dp.toPx()), style = Stroke(sw / 2))
            }
        }
    }
}
