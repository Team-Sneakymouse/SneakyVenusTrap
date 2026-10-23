package com.sneakyvenustrap.service

import java.util.Random

/** Four flowers echo a cry; only the original severs a knot. Pressure is elapsed seconds plus acid. */
class Digestion(private val random: Random, private val knots: Int = 3, private val limit: Int = 100) {
    enum class Hit { ECHO, KNOT, ESCAPED, INACTIVE }
    var original = random.nextInt(4)
        private set
    var pressure = 0
        private set
    var broken = 0
        private set
    val escaped get() = broken >= knots
    val expired get() = pressure >= limit && !escaped
    fun hit(flower: Int): Hit {
        if (escaped || expired || flower !in 0..3) return Hit.INACTIVE
        if (flower != original) { pressure += 20; return Hit.ECHO }
        broken++
        original = (original + 1 + random.nextInt(3)) % 4
        return if (escaped) Hit.ESCAPED else Hit.KNOT
    }
    fun help() { pressure = (pressure - 10).coerceAtLeast(0) }
    fun tick() { if (!escaped) pressure++ }
}
