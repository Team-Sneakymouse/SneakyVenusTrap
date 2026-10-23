package com.sneakyvenustrap.service

/** One health pool for the stationary garden, independent of its rendered mouths. */
class Garden(val maximumHealth: Double) {
    var health = maximumHealth
        private set
    var exposedTicks = 0
        private set
    val dead get() = health <= 0
    fun expose(ticks: Int) { exposedTicks = maxOf(exposedTicks, ticks) }
    fun tick() { if (exposedTicks > 0) exposedTicks-- }
    fun hit(damage: Double): Boolean {
        if (dead || exposedTicks == 0 || !damage.isFinite() || damage <= 0) return false
        health = (health - damage).coerceAtLeast(0.0)
        return true
    }
}
