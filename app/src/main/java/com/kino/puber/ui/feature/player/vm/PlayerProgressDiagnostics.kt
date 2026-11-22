package com.kino.puber.ui.feature.player.vm

import com.kino.puber.core.logger.log
import kotlin.reflect.KClass

internal const val PROGRESS_SAVE_FAILURE_DIAGNOSTIC = "Playback progress save failed"

/**
 * This boundary deliberately accepts only the failure's type, never a throwable, message or media
 * parameter, so progress failures cannot add account viewing identity to application logs.
 */
internal object PlayerProgressDiagnostics {
    fun reportSaveFailure(errorType: KClass<out Throwable>) {
        log("$PROGRESS_SAVE_FAILURE_DIAGNOSTIC: ${errorType.simpleName ?: "unknown"}")
    }
}
