package com.sneakyvenustrap.service

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Metadata only; no microphone audio bytes are retained or decoded. Safe for SVC's network callbacks. */
class MicrophoneActivity(private val clock: () -> Long = System::nanoTime) : AutoCloseable {
    private val heard = ConcurrentHashMap<UUID, Long>()
    @Volatile private var allowed: Set<UUID> = emptySet()
    @Volatile private var closed = false
    @Synchronized fun admit(online: Set<UUID>) {
        if (closed) return
        allowed = online.take(256).toSet()
        heard.keys.retainAll(allowed)
    }
    @Synchronized fun transmitted(player: UUID) {
        if (!closed && player in allowed) heard[player] = clock()
    }
    fun active(player: UUID): Boolean = !closed && player in allowed && heard[player]?.let { clock() - it < 1_000_000_000L } == true
    @Synchronized override fun close() { closed = true; allowed = emptySet(); heard.clear() }
}
