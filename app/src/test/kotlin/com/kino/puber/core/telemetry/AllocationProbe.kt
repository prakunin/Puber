package com.kino.puber.core.telemetry

import java.lang.management.ManagementFactory

/** Bytes allocated by the current thread while running [block], or null when the JVM cannot say. */
internal fun allocatedBytesBy(block: () -> Unit): Long? {
    val bean = ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean ?: return null
    if (!bean.isThreadAllocatedMemorySupported) return null
    bean.isThreadAllocatedMemoryEnabled = true
    val id = Thread.currentThread().id
    val before = bean.getThreadAllocatedBytes(id)
    block()
    return bean.getThreadAllocatedBytes(id) - before
}

internal const val ALLOCATION_BOUND = 64L * 1024

/** A loop that allocates one object per pass, to prove the measurement can see allocation. */
internal fun allocatingLoop(times: Int): Int {
    var sink = 0
    repeat(times) { sink += listOf(it, sink).size }
    return sink
}
