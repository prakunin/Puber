package com.kino.puber.ui.feature.player.vm

import android.app.ActivityManager
import android.content.Context
import com.kino.puber.domain.model.BufferPreset

/**
 * Buffer thresholds handed to `DefaultLoadControl`.
 *
 * ExoPlayer allocates the media buffer on the Java heap, so every preset derives its byte budget
 * from the heap this process actually got (the manifest asks for `largeHeap`) instead of using a
 * flat constant. [BufferParams.prioritizeTimeOverSize] deliberately stays false everywhere: letting
 * the millisecond thresholds win would make a 50 Mbit/s stream allocate hundreds of megabytes
 * before the load control is satisfied. The byte budget is therefore a hard ceiling, and the
 * millisecond thresholds only bind on streams whose bitrate is low enough to fit inside it.
 *
 * The forward buffer and the back buffer draw on the same allocator, and `DefaultLoadControl`
 * compares the total against [BufferParams.targetBufferBytes]. So the two are budgeted separately
 * here: [BufferParams.forwardBufferBytes] is the preset's share of the heap and is never given
 * away, and the back buffer gets an explicit reserve stacked on top of it, sized at
 * [REFERENCE_BITRATE_BYTES_PER_MS]. Folding both into one number is what used to make a bigger
 * preset buffer *less* — at a high enough bitrate the back buffer swallowed the whole budget,
 * `targetBufferBytes` stayed permanently reached, and loading never resumed.
 *
 * When a device cannot afford both, the back buffer is what shrinks. It is a convenience for short
 * rewinds — and one the on-disk media cache already covers — whereas the forward buffer is what
 * playback survives on.
 */
internal object DeviceBufferConfig {
    private const val MIB = 1024 * 1024
    private const val LOW_MEMORY_HEAP_MB = 128
    private const val MEDIUM_MEMORY_HEAP_MB = 256

    // How much of the heap a preset may claim for the forward buffer, as a divisor: bigger divisor,
    // smaller share. MAX is the most aggressive, MEDIUM the most frugal of the sized presets.
    private const val AUTO_HEAP_DIVISOR = 10
    private const val MEDIUM_HEAP_DIVISOR = 12
    private const val LARGE_HEAP_DIVISOR = 8
    private const val MAX_HEAP_DIVISOR = 6

    private const val SMALL_BUFFER_MIB = 16
    private const val AUTO_FLOOR_MIB = 32
    private const val AUTO_CEILING_MIB = 64
    private const val MEDIUM_FLOOR_MIB = 24
    private const val MEDIUM_CEILING_MIB = 48
    private const val LARGE_FLOOR_MIB = 32
    private const val LARGE_CEILING_MIB = 96
    private const val MAX_FLOOR_MIB = 40
    private const val MAX_CEILING_MIB = 128

    // Absolute per-device caps on the forward share, applied after the per-preset clamp. They keep
    // an explicit "Max" pick on a cheap stick from claiming a third of the heap.
    private const val LOW_MEMORY_DEVICE_CEILING_MIB = SMALL_BUFFER_MIB
    private const val MEDIUM_MEMORY_DEVICE_CEILING_MIB = 48

    // Caps on forward plus back-buffer reserve together — the figure that actually lands in the
    // allocator. Each leaves room above the forward cap for a full [BACK_BUFFER_MS] reserve, except
    // on a low-RAM device, where there is no room and the back buffer drops out entirely.
    private const val LOW_MEMORY_TOTAL_CEILING_MIB = SMALL_BUFFER_MIB
    private const val MEDIUM_MEMORY_TOTAL_CEILING_MIB = 72
    private const val TOTAL_CEILING_MIB = 152

    /**
     * The bitrate the back-buffer reserve is sized against — a 4K remux, the top of what the
     * backend serves. Above it the back buffer overruns its reserve and starts costing the forward
     * buffer again, so this is deliberately pessimistic rather than typical.
     */
    private const val REFERENCE_BITRATE_BYTES_PER_MS = 5_000

    /**
     * How much played-out media to keep for instant short rewinds. Uniform across the sized
     * presets: it is worth the same few seconds whatever the forward budget is, and every extra
     * second is charged at [REFERENCE_BITRATE_BYTES_PER_MS] whether the stream needs it or not.
     */
    private const val BACK_BUFFER_MS = 5_000

    data class BufferParams(
        val minBufferMs: Int,
        val maxBufferMs: Int,
        /** Forward plus back-buffer reserve — the figure `DefaultLoadControl` compares against. */
        val targetBufferBytes: Int,
        /** The preset's share of the heap, held for unplayed media and never traded away. */
        val forwardBufferBytes: Int,
        val bufferForPlaybackMs: Int = 2_500,
        val bufferForPlaybackAfterRebufferMs: Int = 5_000,
        val backBufferDurationMs: Int = 0,
        val prioritizeTimeOverSize: Boolean = false,
    )

    /** The two device facts every preset is scaled against. */
    data class DeviceMemory(
        val heapLimitMb: Int,
        val isLowRam: Boolean,
    ) {
        val isLowMemory: Boolean get() = isLowRam || heapLimitMb <= LOW_MEMORY_HEAP_MB
        val isMediumMemory: Boolean get() = !isLowMemory && heapLimitMb <= MEDIUM_MEMORY_HEAP_MB
    }

    fun resolve(context: Context, preset: BufferPreset = BufferPreset.AUTO): BufferParams {
        return resolve(readDeviceMemory(context), preset)
    }

    fun resolve(memory: DeviceMemory, preset: BufferPreset): BufferParams {
        return when (preset) {
            BufferPreset.AUTO -> resolveAuto(memory)
            BufferPreset.SMALL -> params(
                memory = memory,
                forwardMib = SMALL_BUFFER_MIB,
                backBufferMs = 0,
                minBufferMs = 30_000,
                maxBufferMs = 60_000,
            )
            BufferPreset.MEDIUM -> params(
                memory = memory,
                forwardMib = heapShareMib(memory, MEDIUM_HEAP_DIVISOR, MEDIUM_FLOOR_MIB, MEDIUM_CEILING_MIB),
                minBufferMs = 30_000,
                maxBufferMs = 120_000,
            )
            BufferPreset.LARGE -> params(
                memory = memory,
                forwardMib = heapShareMib(memory, LARGE_HEAP_DIVISOR, LARGE_FLOOR_MIB, LARGE_CEILING_MIB),
                minBufferMs = 60_000,
                maxBufferMs = 180_000,
            )
            BufferPreset.MAX -> params(
                memory = memory,
                forwardMib = heapShareMib(memory, MAX_HEAP_DIVISOR, MAX_FLOOR_MIB, MAX_CEILING_MIB),
                minBufferMs = 60_000,
                maxBufferMs = 300_000,
            )
        }
    }

    private fun readDeviceMemory(context: Context): DeviceMemory {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return DeviceMemory(heapLimitMb = am.largeMemoryClass, isLowRam = am.isLowRamDevice)
    }

    private fun resolveAuto(memory: DeviceMemory): BufferParams {
        return when {
            memory.isLowMemory -> resolve(memory, BufferPreset.SMALL)
            memory.isMediumMemory -> params(
                memory = memory,
                forwardMib = heapShareMib(memory, MEDIUM_HEAP_DIVISOR, MEDIUM_FLOOR_MIB, MEDIUM_CEILING_MIB),
                minBufferMs = 30_000,
                maxBufferMs = 90_000,
            )
            else -> params(
                memory = memory,
                forwardMib = heapShareMib(memory, AUTO_HEAP_DIVISOR, AUTO_FLOOR_MIB, AUTO_CEILING_MIB),
                minBufferMs = 45_000,
                maxBufferMs = 120_000,
            )
        }
    }

    /**
     * Stacks a back-buffer reserve on top of the forward share, trimming the back buffer — never
     * the forward share — to whatever the device's total ceiling leaves over.
     */
    private fun params(
        memory: DeviceMemory,
        forwardMib: Int,
        minBufferMs: Int,
        maxBufferMs: Int,
        backBufferMs: Int = BACK_BUFFER_MS,
    ): BufferParams {
        val forwardBytes = minOf(forwardMib, forwardCeilingMib(memory)) * MIB
        val headroomBytes = (totalCeilingMib(memory) * MIB - forwardBytes).coerceAtLeast(0)
        val affordableBackMs = headroomBytes / REFERENCE_BITRATE_BYTES_PER_MS
        val effectiveBackMs = minOf(backBufferMs, affordableBackMs)
        val reserveBytes = effectiveBackMs * REFERENCE_BITRATE_BYTES_PER_MS
        return BufferParams(
            minBufferMs = minBufferMs,
            maxBufferMs = maxBufferMs,
            targetBufferBytes = forwardBytes + reserveBytes,
            forwardBufferBytes = forwardBytes,
            backBufferDurationMs = effectiveBackMs,
        )
    }

    private fun heapShareMib(
        memory: DeviceMemory,
        heapDivisor: Int,
        floorMib: Int,
        ceilingMib: Int,
    ): Int = (memory.heapLimitMb / heapDivisor).coerceIn(floorMib, ceilingMib)

    private fun forwardCeilingMib(memory: DeviceMemory): Int = when {
        memory.isLowMemory -> LOW_MEMORY_DEVICE_CEILING_MIB
        memory.isMediumMemory -> MEDIUM_MEMORY_DEVICE_CEILING_MIB
        else -> MAX_CEILING_MIB
    }

    private fun totalCeilingMib(memory: DeviceMemory): Int = when {
        memory.isLowMemory -> LOW_MEMORY_TOTAL_CEILING_MIB
        memory.isMediumMemory -> MEDIUM_MEMORY_TOTAL_CEILING_MIB
        else -> TOTAL_CEILING_MIB
    }
}
