package com.kino.puber.core.telemetry

/**
 * Records the crash, waits a bounded time for the writer, and always hands the crash on to the
 * handler that was there before, so crash behaviour does not change.
 */
internal class TelemetryUncaughtHandler(
    internal val previous: Thread.UncaughtExceptionHandler?,
    private val sink: TelemetrySink,
) : Thread.UncaughtExceptionHandler {

    @Volatile
    var active = true

    // A crash must never be delayed or hidden by the log; whatever fails here is dropped.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override fun uncaughtException(thread: Thread, error: Throwable) {
        try {
            if (active && Telemetry.isEnabled) {
                Telemetry.record(TelemetryEvent.Err(Telemetry.nowMs(), ErrSource.UNCAUGHT, error))
                if (!sink.executorIsWriter(thread)) sink.flushBlocking(FLUSH_TIMEOUT_MS)
            }
        } catch (_: Throwable) {
            // Dropped on purpose, see above.
        } finally {
            previous?.uncaughtException(thread, error)
        }
    }

    companion object {
        const val FLUSH_TIMEOUT_MS = 1_500L

        fun install(sink: TelemetrySink): TelemetryUncaughtHandler {
            val handler = TelemetryUncaughtHandler(Thread.getDefaultUncaughtExceptionHandler(), sink)
            Thread.setDefaultUncaughtExceptionHandler(handler)
            return handler
        }

        /** Restores the previous handler only if ours is still the default; otherwise ours goes inert. */
        fun uninstall(handler: TelemetryUncaughtHandler) {
            if (Thread.getDefaultUncaughtExceptionHandler() === handler) {
                Thread.setDefaultUncaughtExceptionHandler(handler.previous)
            } else {
                handler.active = false
            }
        }
    }
}
