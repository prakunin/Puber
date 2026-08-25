package com.kino.puber.ui.feature.player.component

import android.content.Context
import android.os.SystemClock
import com.kino.puber.BuildConfig
import java.io.File
import java.util.concurrent.Executors
import org.json.JSONObject

internal const val TIMED_ACTION_DEBUG_FILE = "timed_action_debug.jsonl"

/** Local-build trace of what the timed-action button actually drew on screen. */
internal object TimedActionDebugTrace {

    private val writer = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "timed-action-debug-writer").apply { isDaemon = true }
    }
    private var initialized = false

    fun record(context: Context, snapshot: TimedActionDebugSnapshot) {
        if (BuildConfig.BUILD_TYPE !in TRACE_BUILD_TYPES) return
        val applicationContext = context.applicationContext
        val line = snapshot.toJson().toString()
        writer.execute {
            val outputDirectory = applicationContext.getExternalFilesDir(null) ?: return@execute
            val output = File(outputDirectory, TIMED_ACTION_DEBUG_FILE)
            if (!initialized) {
                output.writeText("")
                initialized = true
            }
            output.appendText(line)
            output.appendText("\n")
        }
    }

    private val TRACE_BUILD_TYPES = setOf("debug", "deploy")
}

internal data class TimedActionDebugSnapshot(
    val event: String,
    val prompt: String,
    val instance: Long,
    val countdown: Int,
    val totalSeconds: Int,
    val progress: Float?,
    val buttonLeft: Float?,
    val buttonTop: Float?,
    val buttonRight: Float?,
    val buttonBottom: Float?,
    val fillLeft: Float?,
    val fillTop: Float?,
    val fillRight: Float?,
    val fillBottom: Float?,
    val cornerRadiusPx: Float?,
) {
    private fun JSONObject.putOptional(name: String, value: Number?) {
        put(name, value ?: JSONObject.NULL)
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("wall_time_ms", System.currentTimeMillis())
        put("uptime_ms", SystemClock.elapsedRealtime())
        put("event", event)
        put("prompt", prompt)
        put("instance", instance)
        put("countdown", countdown)
        put("total_seconds", totalSeconds)
        putOptional("progress", progress)
        putOptional("button_left", buttonLeft)
        putOptional("button_top", buttonTop)
        putOptional("button_right", buttonRight)
        putOptional("button_bottom", buttonBottom)
        putOptional("fill_left", fillLeft)
        putOptional("fill_top", fillTop)
        putOptional("fill_right", fillRight)
        putOptional("fill_bottom", fillBottom)
        putOptional("corner_radius_px", cornerRadiusPx)
    }
}
