package com.kino.puber.core.system

import java.util.concurrent.atomic.AtomicInteger

// aapt reserves the high byte of a resource id, so generated ids stay below it.
private const val MAX_GENERATED_ID = 0x00FFFFFF

object IdGenerator {
    private val nextGeneratedId = AtomicInteger(1)

    fun generateId(): Int {
        while (true) {
            val result = nextGeneratedId.get()
            // aapt-generated IDs have the high byte nonzero; clamp to the range under that.
            var newValue = result + 1
            if (newValue > MAX_GENERATED_ID) newValue = 1 // Roll over to 1, not 0.
            if (nextGeneratedId.compareAndSet(result, newValue)) {
                return result
            }
        }
    }
}