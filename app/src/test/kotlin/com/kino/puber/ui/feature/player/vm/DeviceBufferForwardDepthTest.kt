package com.kino.puber.ui.feature.player.vm

import com.kino.puber.ui.feature.player.model.BufferPreset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The back buffer and the forward buffer draw on the same allocator, so a preset's real forward
 * depth is its byte budget minus whatever the back buffer retains at the stream's bitrate. These
 * tests pin that remainder, because it — not `targetBufferBytes` — is what playback survives on.
 */
internal class DeviceBufferForwardDepthTest {

    private val cheapStick = DeviceBufferConfig.DeviceMemory(heapLimitMb = 128, isLowRam = true)
    private val stick = DeviceBufferConfig.DeviceMemory(heapLimitMb = 256, isLowRam = false)
    private val fireStick = DeviceBufferConfig.DeviceMemory(heapLimitMb = 384, isLowRam = false)
    private val box = DeviceBufferConfig.DeviceMemory(heapLimitMb = 512, isLowRam = false)

    private val everyDevice = listOf(cheapStick, stick, fireStick, box)

    @Test
    fun `every preset still holds a forward buffer on a high bitrate stream`() {
        everyDevice.forEach { memory ->
            BufferPreset.entries.forEach { preset ->
                val depthMs = forwardDepthMs(memory, preset)

                assertTrue(
                    depthMs >= MIN_FORWARD_DEPTH_MS,
                    "$preset on a ${memory.heapLimitMb} MB heap keeps only ${depthMs}ms of forward " +
                        "buffer at $HIGH_BITRATE_LABEL; the back buffer has eaten the budget",
                )
            }
        }
    }

    @Test
    fun `forward depth does not invert as the preset grows`() {
        everyDevice.forEach { memory ->
            val depths = SIZED_PRESETS.map { forwardDepthMs(memory, it) }

            assertEquals(
                depths.sorted(),
                depths,
                "on a ${memory.heapLimitMb} MB heap the bigger preset buffers less at " +
                    "$HIGH_BITRATE_LABEL: ${SIZED_PRESETS.zip(depths)}",
            )
        }
    }

    @Test
    fun `a device too small for a back buffer gives up the back buffer, not the forward budget`() {
        BufferPreset.entries.forEach { preset ->
            val params = DeviceBufferConfig.resolve(cheapStick, preset)

            assertEquals(
                0,
                params.backBufferDurationMs,
                "$preset retains a back buffer it has no byte budget for on a low-RAM device",
            )
        }
    }

    @Test
    fun `the bitrate that would let the back buffer swallow the budget stays out of reach`() {
        everyDevice.forEach { memory ->
            BufferPreset.entries.forEach { preset ->
                val params = DeviceBufferConfig.resolve(memory, preset)
                if (params.backBufferDurationMs == 0) return@forEach

                // Above this the back buffer overruns the reserve sized for it, retakes the whole
                // budget, and DefaultLoadControl stops loading for good — the original defect.
                val mbitPerSecond = params.targetBufferBytes.toDouble() /
                    params.backBufferDurationMs * BYTES_PER_MS_TO_MBIT

                assertTrue(
                    mbitPerSecond >= MIN_DEADLOCK_BITRATE_MBIT,
                    "$preset on a ${memory.heapLimitMb} MB heap deadlocks from " +
                        "%.0f Mbit/s, under the %.0f Mbit/s floor"
                            .format(mbitPerSecond, MIN_DEADLOCK_BITRATE_MBIT),
                )
            }
        }
    }

    /** Milliseconds of unplayed media the preset can hold once the back buffer has taken its share. */
    private fun forwardDepthMs(
        memory: DeviceBufferConfig.DeviceMemory,
        preset: BufferPreset,
    ): Long {
        val params = DeviceBufferConfig.resolve(memory, preset)
        val backBufferBytes = params.backBufferDurationMs * HIGH_BITRATE_BYTES_PER_MS
        return (params.targetBufferBytes - backBufferBytes) / HIGH_BITRATE_BYTES_PER_MS
    }

    private companion object {
        // A 4K remux — the top of what the backend serves, and the case the byte budget must survive.
        const val HIGH_BITRATE_BYTES_PER_MS = 5_000L
        const val HIGH_BITRATE_LABEL = "40 Mbit/s"

        // Below this a stream cannot ride out even a single reconnect.
        const val MIN_FORWARD_DEPTH_MS = 3_000L

        // bytes/ms -> Mbit/s
        const val BYTES_PER_MS_TO_MBIT = 0.008

        // Comfortably past a UHD Blu-ray remux, so no stream the backend serves can reach it.
        const val MIN_DEADLOCK_BITRATE_MBIT = 75.0

        val SIZED_PRESETS = listOf(
            BufferPreset.SMALL,
            BufferPreset.MEDIUM,
            BufferPreset.LARGE,
            BufferPreset.MAX,
        )
    }
}
