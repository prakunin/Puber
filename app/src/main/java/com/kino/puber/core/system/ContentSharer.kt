package com.kino.puber.core.system

import android.content.Context
import android.content.Intent

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

    private companion object {
        const val MIME_TEXT = "text/plain"
    }
}
