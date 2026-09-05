package com.kino.puber.ui.feature.player.vm

import com.kino.puber.domain.model.BufferPreset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class DeviceBufferConfigTest {

    private val stick = DeviceBufferConfig.DeviceMemory(heapLimitMb = 256, isLowRam = false)
    private val box = DeviceBufferConfig.DeviceMemory(heapLimitMb = 512, isLowRam = false)
    private val flagship = DeviceBufferConfig.DeviceMemory(heapLimitMb = 768, isLowRam = false)
    private val cheapStick = DeviceBufferConfig.DeviceMemory(heapLimitMb = 128, isLowRam = true)

    @Test
    fun `byte budget grows monotonically with the preset`() {
        listOf(stick, box, flagship).forEach { memory ->
            val budgets = listOf(
                BufferPreset.SMALL,
                BufferPreset.MEDIUM,
                BufferPreset.LARGE,
                BufferPreset.MAX,
            ).map { DeviceBufferConfig.resolve(memory, it).targetBufferBytes }

            assertEquals(
                budgets.sorted(),
                budgets,
                "presets must not invert on a ${memory.heapLimitMb} MB heap: $budgets",
            )
            assertTrue(
                budgets.last() > budgets.first(),
                "MAX must exceed SMALL on a ${memory.heapLimitMb} MB heap",
            )
        }
    }

    @Test
    fun `byte budget grows with the heap`() {
        BufferPreset.entries.forEach { preset ->
            val onStick = DeviceBufferConfig.resolve(stick, preset).targetBufferBytes
            val onFlagship = DeviceBufferConfig.resolve(flagship, preset).targetBufferBytes

            assertTrue(
                onFlagship >= onStick,
                "$preset must not shrink on a bigger heap: $onStick -> $onFlagship",
            )
        }
    }

    @Test
    fun `auto scales past the flat 32 MiB on a capable device`() {
        val onStick = DeviceBufferConfig.resolve(stick, BufferPreset.AUTO).forwardBufferBytes
        val onFlagship = DeviceBufferConfig.resolve(flagship, BufferPreset.AUTO).forwardBufferBytes

        assertEquals(24 * MIB, onStick)
        assertEquals(64 * MIB, onFlagship)
    }

    @Test
    fun `low memory device caps every preset at the small budget`() {
        BufferPreset.entries.forEach { preset ->
            val params = DeviceBufferConfig.resolve(cheapStick, preset)

            assertEquals(
                16 * MIB,
                params.targetBufferBytes,
                "$preset must stay at the small budget on a low-RAM device",
            )
        }
    }

    @Test
    fun `no preset lets time thresholds outrank the byte budget`() {
        BufferPreset.entries.forEach { preset ->
            assertFalse(
                DeviceBufferConfig.resolve(box, preset).prioritizeTimeOverSize,
                "$preset would let a high-bitrate stream allocate past its byte budget",
            )
        }
    }

    @Test
    fun `thresholds satisfy the DefaultLoadControl ordering`() {
        listOf(cheapStick, stick, box, flagship).forEach { memory ->
            BufferPreset.entries.forEach { preset ->
                val params = DeviceBufferConfig.resolve(memory, preset)
                val label = "$preset on a ${memory.heapLimitMb} MB heap"

                assertTrue(params.minBufferMs <= params.maxBufferMs, "$label: min above max")
                assertTrue(
                    params.bufferForPlaybackMs <= params.minBufferMs,
                    "$label: playback threshold above min",
                )
                assertTrue(
                    params.bufferForPlaybackAfterRebufferMs <= params.minBufferMs,
                    "$label: rebuffer threshold above min",
                )
                assertTrue(params.targetBufferBytes > 0, "$label: empty byte budget")
            }
        }
    }

    private companion object {
        const val MIB = 1024 * 1024
    }
}
