package com.kino.puber.core.system

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

internal class ContentSharer(
    private val context: Context,
) {
    fun share(url: String, chooserTitle: String): Boolean = runCatching {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = MIME_TEXT
            putExtra(Intent.EXTRA_TEXT, url)
        }
        val chooser = Intent.createChooser(sendIntent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }.isSuccess

    /** Shares [file] through the update FileProvider, which also covers the telemetry directory. */
    fun shareFile(file: File, mimeType: String, chooserTitle: String): Boolean = runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.update_files", file)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(sendIntent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(chooser)
    }.isSuccess

    private companion object {
        const val MIME_TEXT = "text/plain"
    }
}
