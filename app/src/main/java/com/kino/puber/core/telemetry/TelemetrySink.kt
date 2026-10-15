package com.kino.puber.core.telemetry

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicIntegerArray
import kotlin.random.Random

/**
 * Producers only enqueue an immutable event; the writer thread does everything else. The queue is
 * bounded: past [QUEUE_BOUND] events are dropped and counted per type.
 */
internal class TelemetrySink(
    private val clock: TelemetryClock,
    private val store: TelemetryStore,
    private val executor: TelemetryExecutor,
    private val sessionInfo: () -> SessionInfo,
    sid: String = randomSid(),
) : TelemetryRecorder {

    @Volatile
    override var isEnabled: Boolean = false
        private set

    private val queue = ConcurrentLinkedQueue<TelemetryEvent>()
    private val queueSize = AtomicInteger()
    private val overflow = AtomicIntegerArray(TelemetryEventType.entries.size)
    private val mutableStatus = MutableStateFlow<SessionLogState>(SessionLogState.Off(hasFiles = false))

    // Writer-thread state.
    private var currentSid = sid
    private var firstSessionStarted = false
    private var writerOrNull: TelemetryWriter? = null
    private val writer: TelemetryWriter get() = writerOrNull ?: newWriter(currentSid, clock.elapsedMs())
    private var running = false
    private var unavailable = false
    private var enabledAtWallMs = 0L
    private var originElapsedMs = 0L
    private var onExpired: () -> Unit = {}
    private var onStopped: () -> Unit = {}
    private var repeating: AutoCloseable? = null

    val status: StateFlow<SessionLogState> = mutableStatus

    override fun record(event: TelemetryEvent) {
        if (!isEnabled) return
        if (queueSize.incrementAndGet() > QUEUE_BOUND) {
            queueSize.decrementAndGet()
            overflow.incrementAndGet(event.type.ordinal)
            return
        }
        queue.offer(event)
    }

    /**
     * Turns recording on. [onExpired] runs on the writer thread after the seven days ran out;
     * [onStopped] when recording stopped by itself because the storage failed.
     */
    fun start(enabledAtWallMs: Long, onExpired: () -> Unit, onStopped: () -> Unit) {
        // The time origin of the session is taken before the first event can be captured.
        val origin = clock.elapsedMs()
        isEnabled = true
        executor.execute { startOnWriter(enabledAtWallMs, origin, onExpired, onStopped) }
    }

    fun stop() {
        isEnabled = false
        executor.execute(::stopOnWriter)
    }

    /** Pause hook: drain soon without waiting for the next tick. */
    fun requestDrain() {
        if (isEnabled) executor.execute { drain(force = false) }
    }

    /** Drains and flushes on the writer thread and waits. False on the writer thread or on timeout. */
    fun flushBlocking(timeoutMs: Long): Boolean {
        if (executor.isWriterThread()) return false
        val latch = CountDownLatch(1)
        executor.execute {
            try {
                drain(force = true)
            } finally {
                latch.countDown()
            }
        }
        return latch.await(timeoutMs, TimeUnit.MILLISECONDS)
    }

    fun executorIsWriter(thread: Thread): Boolean = executor.isWriterThread(thread)

    /** Runs on the writer even when recording is off. Always calls [onResult]. */
    fun export(onResult: (ShareResult) -> Unit) {
        executor.execute {
            val result = try {
                exportOnWriter()
            } catch (@Suppress("TooGenericExceptionCaught") _: Throwable) {
                ShareResult.Failed
            }
            onResult(result)
        }
    }

    // Whatever the user is reporting must be in the archive: queued events and folds come first.
    private fun exportOnWriter(): ShareResult {
        if (running) {
            drainQueue()
            writer.finishDrain(force = true)
        }
        return writer.export()
    }

    private fun newWriter(sessionSid: String, origin: Long): TelemetryWriter {
        val names = TelemetryNames()
        val encoder = TelemetryEncoder(sessionSid, origin, names, sessionInfo, clock::wallMs)
        return TelemetryWriter(clock, store, encoder, names, sessionSid, onUnavailable = ::onWriterUnavailable)
            .also { writerOrNull = it }
    }

    fun refreshStatus() {
        executor.execute(::publishStatus)
    }

    fun schedule(delayMs: Long, task: () -> Unit) {
        executor.schedule(delayMs, task)
    }

    private fun startOnWriter(enabledAt: Long, origin: Long, expired: () -> Unit, stopped: () -> Unit) {
        enabledAtWallMs = enabledAt
        originElapsedMs = origin
        dropEventsOlderThanOrigin()
        onExpired = expired
        onStopped = stopped
        repeating?.close()
        repeating = null
        // Every recording session has its own sid and time origin.
        if (firstSessionStarted) currentSid = randomSid()
        firstSessionStarted = true
        newWriter(currentSid, origin)
        unavailable = false
        if (!store.isAvailable()) {
            onWriterUnavailable()
            return
        }
        running = true
        writer.write(TelemetryEvent.Session(clock.elapsedMs()))
        writer.write(TelemetryEvent.Meta(clock.elapsedMs(), MetaEv.START))
        repeating = executor.scheduleRepeating(DRAIN_PERIOD_MS) { drain(force = false) }
        publishStatus()
    }

    private fun stopOnWriter() {
        if (running) {
            running = false
            drainQueue()
            writer.finishDrain(force = true)
            writer.write(TelemetryEvent.Meta(clock.elapsedMs(), MetaEv.STOP))
            writer.closeFile()
        }
        unavailable = false
        repeating?.close()
        repeating = null
        publishStatus()
    }

    private fun drain(force: Boolean) {
        if (!running) return
        drainQueue()
        writer.finishDrain(force)
        if (!running) return
        if (expired()) {
            expire()
            return
        }
        publishStatus()
    }

    private fun drainQueue() {
        var event = queue.poll()
        while (event != null) {
            queueSize.decrementAndGet()
            if (event.t >= originElapsedMs) writer.write(event)
            event = queue.poll()
        }
        for (type in TelemetryEventType.entries) {
            val count = overflow.getAndSet(type.ordinal, 0)
            if (count > 0) writer.noteDropped(type, count)
        }
    }

    // A producer that passed the enabled check before the previous session ended can enqueue late.
    private fun dropEventsOlderThanOrigin() {
        val iterator = queue.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().t >= originElapsedMs) continue
            iterator.remove()
            queueSize.decrementAndGet()
        }
    }

    private fun expired(): Boolean {
        // A clock behind the switch time (no RTC before time sync) is not expiry; it stays as is.
        // Recorded time in this process is the second bound, so a wrong future switch time cannot
        // hold the log on for longer than seven days of uptime.
        return clock.wallMs() - enabledAtWallMs >= SEVEN_DAYS_MS ||
            clock.elapsedMs() - originElapsedMs >= SEVEN_DAYS_MS
    }

    private fun expire() {
        isEnabled = false
        running = false
        while (queue.poll() != null) queueSize.decrementAndGet()
        writer.write(TelemetryEvent.Meta(clock.elapsedMs(), MetaEv.AUTO_OFF))
        writer.finishDrain()
        writer.closeFile()
        repeating?.close()
        repeating = null
        publishStatus()
        onExpired()
    }

    private fun onWriterUnavailable() {
        isEnabled = false
        running = false
        unavailable = true
        repeating?.close()
        repeating = null
        while (queue.poll() != null) queueSize.decrementAndGet()
        mutableStatus.value = SessionLogState.Unavailable
        onStopped()
    }

    private fun publishStatus() {
        mutableStatus.value = when {
            unavailable -> SessionLogState.Unavailable
            running -> SessionLogState.Recording(remainingMs(), hasFiles())
            else -> SessionLogState.Off(hasFiles())
        }
    }

    private fun remainingMs(): Long {
        val byWall = enabledAtWallMs + SEVEN_DAYS_MS - clock.wallMs()
        val byUptime = originElapsedMs + SEVEN_DAYS_MS - clock.elapsedMs()
        val remaining = minOf(byWall, byUptime).coerceIn(0, SEVEN_DAYS_MS)
        return remaining / HOUR_MS * HOUR_MS
    }

    private fun hasFiles(): Boolean = try {
        store.list().any { it.name.endsWith(".jsonl") }
    } catch (_: IOException) {
        false
    }

    companion object {
        const val QUEUE_BOUND = 4096
        const val DRAIN_PERIOD_MS = 5_000L
        const val SEVEN_DAYS_MS = 7L * 24 * 60 * 60 * 1000
        private const val HOUR_MS = 60L * 60 * 1000

        fun randomSid(): String = Random.nextInt().toUInt().toString(HEX_RADIX).padStart(SID_LENGTH, '0')

        private const val HEX_RADIX = 16
        private const val SID_LENGTH = 8
    }
}
