package com.sneakyvenustrap.service

import java.util.Random
import java.util.UUID

class Bribes(private val random: Random, private val cooldown: Long, private val duration: Long) {
    enum class Result { COOLDOWN, REJECTED, TRUCE }
    private val offered = mutableMapOf<UUID, Long>()
    private val peaceful = mutableMapOf<UUID, Long>()
    fun offer(player: UUID, now: Long): Result {
        offered.entries.removeIf { now - it.value >= cooldown }
        peaceful.entries.removeIf { now >= it.value }
        if (player in offered) return Result.COOLDOWN
        offered[player] = now
        if (random.nextInt(5) != 0) return Result.REJECTED
        peaceful[player] = now + duration
        return Result.TRUCE
    }
    fun truce(player: UUID, now: Long) = now < (peaceful[player] ?: Long.MIN_VALUE)
}
