package com.offerpk.photoresizer.image

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

object ImageSaver {
    const val ALBUM = "PhotoResizer"

    /** True when saving needs the legacy WRITE_EXTERNAL_STORAGE permission (API 24–28). */
    val needsLegacyPermission: Boolean get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    fun saveToGallery(context: Context, bytes: ByteArray, mime: String, fileName: String): Uri {
        val cr = context.contentResolver
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, mime)
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = cr.insert(collection, values) ?: error("Could not create gallery entry")
            try {
                cr.openOutputStream(uri)?.use { it.write(bytes) } ?: error("Could not open output")
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                cr.update(uri, values, null, null)
                uri
            } catch (t: Throwable) {
                cr.delete(uri, null, null)
                throw t
            }
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), ALBUM)
            if (!dir.exists() && !dir.mkdirs()) error("Could not create ${dir.path}")
            var file = File(dir, fileName)
            var n = 1
            while (file.exists()) {
                file = File(dir, fileName.substringBeforeLast('.') + "_" + n++ + "." + fileName.substringAfterLast('.'))
            }
            file.writeBytes(bytes)
            @Suppress("DEPRECATION")
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DATA, file.absolutePath)
                put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
                put(MediaStore.Images.Media.MIME_TYPE, mime)
            }
            val uri = runCatching { cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) }.getOrNull()
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mime), null)
            uri ?: Uri.fromFile(file)
        }
    }

    fun share(context: Context, bytes: ByteArray, mime: String, fileName: String) {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, fileName)
        file.writeBytes(bytes)
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = android.content.ClipData.newRawUri(fileName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share image").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
