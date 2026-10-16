package com.kino.puber.core.telemetry

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.view.Display
import com.kino.puber.BuildConfig
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import kotlin.coroutines.resume

/**
 * Owns the on/off decision: the persisted switch, the seven-day limit at start, and the probes
 * (error tree, uncaught handler, frame, key and memory probes) that exist only while recording.
 * [probes] need the main thread, so [mainPost] carries their attach and detach there.
 */
internal class TelemetryRuntime(
    private val prefs: SharedPreferences,
    private val clock: TelemetryClock,
    private val sink: TelemetrySink,
    private val probes: List<TelemetryProbe> = emptyList(),
    private val mainPost: (Runnable) -> Unit = { it.run() },
    private val isMainThread: () -> Boolean = { true },
) : SessionLogController {

    private var tree: TelemetryErrorTree? = null
    private var handler: TelemetryUncaughtHandler? = null

    override val state: StateFlow<SessionLogState> = sink.status

    /**
     * Reads the switch. With no key nothing else happens: no executor, no directory, no tree, no
     * handler. [startup] builds the cold-start marks and is called only when recording resumes.
     */
    fun start(startup: (() -> StartupMarks)?) {
        if (!prefs.contains(KEY_ENABLED_AT)) return
        val now = clock.wallMs()
        // A stored time ahead of the clock (no RTC before time sync) is kept as it is and counts as
        // no time elapsed; it is never rewritten, so the seven days run from the real switch time.
        val enabledAt = prefs.getLong(KEY_ENABLED_AT, now)
        if (now - enabledAt >= TelemetrySink.SEVEN_DAYS_MS) {
            prefs.edit().remove(KEY_ENABLED_AT).apply()
            return
        }
        enable(enabledAt)
        val marks = startup?.invoke() ?: return
        Telemetry.startupMarks = marks
        sink.schedule(STARTUP_FALLBACK_MS) { marks.onTimeout() }
    }

    @Synchronized
    override fun setEnabled(enabled: Boolean) {
        if (enabled) {
            if (sink.isEnabled) return
            val now = clock.wallMs()
            prefs.edit().putLong(KEY_ENABLED_AT, now).apply()
            enable(now)
        } else {
            prefs.edit().remove(KEY_ENABLED_AT).apply()
            // The probes close their open windows while the sink still takes events.
            flushProbesInline()
            sink.stop()
            detachProbes()
        }
    }

    override fun refresh() {
        sink.requestDrain()
        sink.refreshStatus()
    }

    override suspend fun share(): ShareResult = suspendCancellableCoroutine { continuation ->
        sink.export { result -> continuation.resume(result) }
    }

    // Probes first: if the storage turns out to be unavailable, the sink's callback must find
    // them planted and remove them, not the other way round.
    @Synchronized
    private fun enable(enabledAt: Long) {
        plantProbes()
        sink.start(enabledAt, onExpired = ::onExpired, onStopped = ::detachProbes)
    }

    private fun onExpired() {
        prefs.edit().remove(KEY_ENABLED_AT).apply()
        detachProbes()
    }

    @Synchronized
    private fun plantProbes() {
        if (tree == null) tree = TelemetryErrorTree().also(Timber::plant)
        if (handler == null) handler = TelemetryUncaughtHandler.install(sink)
        // Queued behind any earlier detach; a toggle-off that ran meanwhile must win.
        if (probes.isNotEmpty()) {
            mainPost(Runnable { if (sink.isEnabled) probes.forEach(TelemetryProbe::attach) })
        }
    }

    private fun flushProbesInline() {
        if (probes.isNotEmpty() && isMainThread()) probes.forEach(TelemetryProbe::detach)
    }

    @Synchronized
    internal fun detachProbes() {
        tree?.let(Timber::uproot)
        tree = null
        handler?.let(TelemetryUncaughtHandler::uninstall)
        handler = null
        Telemetry.screen = null
        // Also reached on the writer thread (expiry, storage failure): never touch the probes inline.
        // Idempotent after the inline flush, and it must trail an attach queued before it.
        if (probes.isNotEmpty()) mainPost(Runnable { probes.forEach(TelemetryProbe::detach) })
    }

    companion object {
        const val PREFS_NAME = "telemetry_preferences"
        const val KEY_ENABLED_AT = "enabled_at_wall_ms"
        private const val STARTUP_FALLBACK_MS = 60_000L

        fun install(app: Application, appCreateMs: Long) {
            val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val executor = ThreadTelemetryExecutor()
            val sink = TelemetrySink(
                clock = SystemTelemetryClock,
                store = FileTelemetryStore { app.getExternalFilesDir(null) },
                executor = executor,
                sessionInfo = { sessionInfo(app) },
            )
            val memoryProbe = MemoryProbe(
                executor,
                DebugMemorySampler(),
                app::registerComponentCallbacks,
                app::unregisterComponentCallbacks,
            )
            val frameProbe = FrameProbe()
            val callbacks = TelemetryActivityCallbacks(
                { Telemetry.isEnabled },
                frameProbe,
                KeyProbe::flush,
                sink::requestDrain,
                memoryProbe,
            )
            val main = Handler(Looper.getMainLooper())
            val probes = listOf(ActivityProbes(callbacks, frameProbe, memoryProbe, KeyProbe::detach))
            val runtime = TelemetryRuntime(
                prefs,
                SystemTelemetryClock,
                sink,
                probes,
                mainPost = { main.post(it) },
                isMainThread = { Looper.myLooper() === Looper.getMainLooper() },
            )
            Telemetry.install(sink, SystemTelemetryClock)
            Telemetry.installController(runtime)
            runtime.start {
                StartupMarks(Process.getStartElapsedRealtime(), appCreateMs, sink::record, SystemTelemetryClock)
            }
            app.registerActivityLifecycleCallbacks(callbacks)
        }

        // Evaluated on the writer thread only.
        private fun sessionInfo(app: Application): SessionInfo {
            val activityManager = app.getSystemService(ActivityManager::class.java)
            val display = app.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
            return SessionInfo(
                build = BuildConfig.BUILD_TYPE,
                ver = BuildConfig.VERSION_NAME,
                api = Build.VERSION.SDK_INT,
                model = Build.MODEL,
                ramClass = activityManager?.memoryClass ?: 0,
                lowRam = activityManager?.isLowRamDevice ?: false,
                hz = display?.refreshRate ?: 0f,
            )
        }
    }
}
