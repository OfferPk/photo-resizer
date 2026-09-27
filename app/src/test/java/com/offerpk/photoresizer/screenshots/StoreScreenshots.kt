package com.offerpk.photoresizer.screenshots

import android.app.Application
import android.graphics.RectF
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.offerpk.photoresizer.AppViewModel
import com.offerpk.photoresizer.image.ImageLoader
import com.offerpk.photoresizer.image.ImageOps
import com.offerpk.photoresizer.image.SourceImage
import com.offerpk.photoresizer.ui.components.AspectCropState
import com.offerpk.photoresizer.ui.screens.AboutScreen
import com.offerpk.photoresizer.ui.screens.Bg
import com.offerpk.photoresizer.ui.screens.CompressScreen
import com.offerpk.photoresizer.ui.screens.HomeScreen
import com.offerpk.photoresizer.ui.screens.Ink
import com.offerpk.photoresizer.ui.screens.PassportScreen
import com.offerpk.photoresizer.ui.screens.ResizeScreen
import com.offerpk.photoresizer.ui.screens.ResultScreen
import com.offerpk.photoresizer.ui.screens.SignatureScreen
import com.offerpk.photoresizer.ui.screens.compress
import com.offerpk.photoresizer.ui.screens.exportSignature
import com.offerpk.photoresizer.ui.screens.sheet
import com.offerpk.photoresizer.ui.theme.PhotoResizerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the REAL app screens (same composables the app uses) with real sample photos
 * and real processing results, at 1080x1920 (360x640dp @ xxhdpi). Used for store screenshots.
 *
 *   ./gradlew :app:testDebugUnitTest --tests '*StoreScreenshots*' -Pscreenshots
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xxhdpi")
class StoreScreenshots {
    @get:Rule val compose = createComposeRule()

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val samples = File(System.getProperty("samples.dir")!!)
    private val out = File(System.getProperty("screenshots.out")!!).apply { mkdirs() }

    private fun load(name: String): SourceImage = ImageLoader.load(app, Uri.fromFile(File(samples, name)))

    private fun vmWith(src: SourceImage?) = AppViewModel(app).also { vm -> src?.let { vm.useSource(it) } }

    private fun shot(name: String, content: @Composable () -> Unit) {
        compose.setContent { PhotoResizerTheme(dark = false) { content() } }
        repeat(12) { Thread.sleep(250); compose.mainClock.advanceTimeBy(500); compose.waitForIdle() }
        compose.onRoot().captureRoboImage(File(out, "$name.png").absolutePath)
    }

    @Test fun s1_home() = shot("01-home") { HomeScreen(onTool = {}, onAbout = {}) }

    @Test fun s2_compress() {
        val vm = vmWith(load("landscape.jpg"))
        shot("02-compress") { CompressScreen(vm, {}, {}, {}) }
    }

    @Test fun s3_compressResult() {
        val src = load("landscape.jpg")
        val vm = vmWith(src)
        vm.showResult(compress(src, 50, keepDims = false))
        shot("03-compress-result") { ResultScreen(vm, {}, {}) }
    }

    @Test fun s4_passport() {
        val vm = vmWith(load("portrait.jpg"))
        shot("04-passport") { PassportScreen(vm, {}, {}, {}) }
    }

    @Test fun s5_passportSheet() {
        val src = load("portrait.jpg")
        val vm = vmWith(src)
        val crop: RectF = AspectCropState(src.bitmap.width, src.bitmap.height, 35f / 45f).cropRect()
        vm.showResult(sheet(src, crop, 413, 531, whiten = true, tol = 40, wMm = 35.0, hMm = 45.0))
        shot("05-passport-sheet") { ResultScreen(vm, {}, {}) }
    }

    @Test fun s6_signature() {
        val vm = vmWith(load("signature.jpg"))
        shot("06-signature") { SignatureScreen(vm, {}, {}, {}) }
    }

    @Test fun s7_signatureResult() {
        val src = load("signature.jpg")
        val vm = vmWith(src)
        val small = ImageOps.normalizeLighting(ImageOps.toLuma(src.bitmap), src.bitmap.width, src.bitmap.height)
        val t = ImageOps.otsuThreshold(small).coerceIn(60, 230)
        vm.showResult(exportSignature(src, Rect(0.05f, 0.2f, 0.95f, 0.8f), t, Ink.BLACK, Bg.TRANSPARENT, true, 600))
        shot("07-signature-result") { ResultScreen(vm, {}, {}) }
    }

    @Test fun s8_resize() {
        val vm = vmWith(load("landscape.jpg"))
        shot("08-resize") { ResizeScreen(vm, {}, {}, {}) }
    }

    @Test fun s9_about() = shot("09-about") { AboutScreen(onBack = {}) }
}
