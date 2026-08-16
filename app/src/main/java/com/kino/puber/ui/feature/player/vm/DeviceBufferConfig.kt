package com.kino.puber.ui.feature.player.vm

import android.app.ActivityManager
import android.content.Context
import com.kino.puber.ui.feature.player.model.BufferPreset

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
 * The same budget also covers the back buffer, so a preset's forward buffer is its byte budget
 * minus whatever [BufferParams.backBufferDurationMs] retains.
 */
internal object DeviceBufferConfig {
    private const val MIB = 1024 * 1024
    private const val LOW_MEMORY_HEAP_MB = 128
    private const val MEDIUM_MEMORY_HEAP_MB = 256

    // How much of the heap a preset may claim for buffering, as a divisor: bigger divisor,
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

    // Absolute per-device caps, applied after the per-preset clamp. They keep an explicit "Max"
    // pick on a cheap stick from claiming a third of the heap.
    private const val LOW_MEMORY_DEVICE_CEILING_MIB = SMALL_BUFFER_MIB
    private const val MEDIUM_MEMORY_DEVICE_CEILING_MIB = 48

    private const val SHORT_BACK_BUFFER_MS = 5_000
    private const val DEFAULT_BACK_BUFFER_MS = 10_000
    private const val LARGE_BACK_BUFFER_MS = 15_000
    private const val MAX_BACK_BUFFER_MS = 30_000

    data class BufferParams(
        val minBufferMs: Int,
        val maxBufferMs: Int,
        val targetBufferBytes: Int,
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
            BufferPreset.SMALL -> BufferParams(
                minBufferMs = 30_000,
                maxBufferMs = 60_000,
                targetBufferBytes = fixedBudgetBytes(memory, SMALL_BUFFER_MIB),
                backBufferDurationMs = 0,
            )
            BufferPreset.MEDIUM -> BufferParams(
                minBufferMs = 30_000,
                maxBufferMs = 120_000,
                targetBufferBytes = budgetBytes(
                    memory,
                    MEDIUM_HEAP_DIVISOR,
                    MEDIUM_FLOOR_MIB,
                    MEDIUM_CEILING_MIB,
                ),
                backBufferDurationMs = SHORT_BACK_BUFFER_MS,
            )
            BufferPreset.LARGE -> BufferParams(
                minBufferMs = 60_000,
                maxBufferMs = 180_000,
                targetBufferBytes = budgetBytes(
                    memory,
                    LARGE_HEAP_DIVISOR,
                    LARGE_FLOOR_MIB,
                    LARGE_CEILING_MIB,
                ),
                backBufferDurationMs = LARGE_BACK_BUFFER_MS,
            )
            BufferPreset.MAX -> BufferParams(
                minBufferMs = 60_000,
                maxBufferMs = 300_000,
                targetBufferBytes = budgetBytes(
                    memory,
                    MAX_HEAP_DIVISOR,
                    MAX_FLOOR_MIB,
                    MAX_CEILING_MIB,
                ),
                backBufferDurationMs = MAX_BACK_BUFFER_MS,
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
            memory.isMediumMemory -> BufferParams(
                minBufferMs = 30_000,
                maxBufferMs = 90_000,
                targetBufferBytes = budgetBytes(
                    memory,
                    MEDIUM_HEAP_DIVISOR,
                    MEDIUM_FLOOR_MIB,
                    MEDIUM_CEILING_MIB,
                ),
                backBufferDurationMs = SHORT_BACK_BUFFER_MS,
            )
            else -> BufferParams(
                minBufferMs = 45_000,
                maxBufferMs = 120_000,
                targetBufferBytes = budgetBytes(
                    memory,
                    AUTO_HEAP_DIVISOR,
                    AUTO_FLOOR_MIB,
                    AUTO_CEILING_MIB,
                ),
                backBufferDurationMs = DEFAULT_BACK_BUFFER_MS,
            )
        }
    }

    private fun budgetBytes(
        memory: DeviceMemory,
        heapDivisor: Int,
        floorMib: Int,
        ceilingMib: Int,
    ): Int {
        val share = memory.heapLimitMb / heapDivisor
        return fixedBudgetBytes(memory, share.coerceIn(floorMib, ceilingMib))
    }

    private fun fixedBudgetBytes(memory: DeviceMemory, mib: Int): Int {
        return minOf(mib, deviceCeilingMib(memory)) * MIB
    }

    private fun deviceCeilingMib(memory: DeviceMemory): Int = when {
        memory.isLowMemory -> LOW_MEMORY_DEVICE_CEILING_MIB
        memory.isMediumMemory -> MEDIUM_MEMORY_DEVICE_CEILING_MIB
        else -> MAX_CEILING_MIB
    }
}
