package com.sneakyvenustrap.service

/** One admitted target; elapsed time is supplied by the main-thread owner. */
class PlantAttack {
    enum class Phase { IDLE, WINDUP, CONTACT, RECOVERY }
    var phase = Phase.IDLE
        private set
    var elapsed = 0
        private set
    private var windup = 30
    fun start(swallow: Boolean = false) { phase = Phase.WINDUP; elapsed = 0; windup = if (swallow) 50 else 30 }
    fun tick(valid: Boolean): Boolean {
        if (!valid) { reset(); return false }
        if (phase == Phase.IDLE) return false
        elapsed += 5
        phase = when {
            elapsed < windup -> Phase.WINDUP
            elapsed == windup -> Phase.CONTACT
            elapsed < windup + 50 -> Phase.RECOVERY
            else -> Phase.IDLE
        }
        return phase == Phase.CONTACT
    }
    fun reset() { phase = Phase.IDLE; elapsed = 0 }
}
