package com.offerpk.photoresizer

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.offerpk.photoresizer.image.ImageLoader
import com.offerpk.photoresizer.image.SourceImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The file produced by a tool, shown on the Result screen. */
class ExportResult(
    val bytes: ByteArray,
    val mime: String,
    val width: Int,
    val height: Int,
    val fileName: String,
    val title: String,
    val beforeBytes: Long?,
    val beforeWidth: Int?,
    val beforeHeight: Int?,
    val details: List<String> = emptyList(),
    val warning: String? = null,
    val transparent: Boolean = false,
    /** Show the "% smaller" badge (off for outputs that aren't a smaller copy, e.g. print sheets). */
    val showSavings: Boolean = true,
) {
    val preview: Bitmap? by lazy { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    var source by mutableStateOf<SourceImage?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
    var result by mutableStateOf<ExportResult?>(null)
        private set
    /** Whether the current result has been saved or shared. */
    var exported by mutableStateOf(false)
    var savedUri by mutableStateOf<Uri?>(null)

    /** Directly sets the working photo (used by load() and by screenshot tests). */
    fun useSource(img: SourceImage) { source = img }

    /** Publishes a finished result (used by process() and by screenshot tests). */
    fun showResult(r: ExportResult) {
        result = r
        exported = false
        savedUri = null
    }

    fun load(uri: Uri, onLoaded: () -> Unit = {}) {
        loading = true
        viewModelScope.launch {
            try {
                val img = withContext(Dispatchers.IO) { ImageLoader.load(getApplication(), uri) }
                source = img
                onLoaded()
            } catch (t: Throwable) {
                error = "Couldn't open this image. Please try another photo."
            } finally {
                loading = false
            }
        }
    }

    /** Runs heavy image work off the main thread and publishes the result. */
    fun process(work: suspend () -> ExportResult, onDone: () -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                val r = withContext(Dispatchers.Default) { work() }
                showResult(r)
                onDone()
            } catch (oom: OutOfMemoryError) {
                error = "Not enough memory for this image. Try a smaller photo or size."
            } catch (t: Throwable) {
                error = t.message ?: "Something went wrong."
            } finally {
                busy = false
            }
        }
    }
}
