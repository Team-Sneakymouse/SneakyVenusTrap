package com.sneakyvenustrap.service

/** One admitted target; timings and pose progress share this five-tick state machine. */
class PlantAttack {
    enum class Phase { IDLE, WINDUP, CONTACT, RECOVERY }
    var phase = Phase.IDLE
        private set
    var elapsed = 0
        private set
    var windupTicks = 30
        private set
    var recoveryTicks = 50
        private set
    private var swallowing = false
    val inhaling get() = swallowing && phase == Phase.WINDUP && elapsed in 20 until 35
    val progress get() = when (phase) {
        Phase.IDLE -> 0f
        Phase.WINDUP -> elapsed.toFloat() / windupTicks * 0.35f
        else -> (1f - (elapsed - windupTicks).toFloat() / recoveryTicks).coerceIn(0f, 1f)
    }
    fun start(swallow: Boolean = false, rage: Boolean = false) {
        phase = Phase.WINDUP; elapsed = 0; swallowing = swallow
        windupTicks = if (swallow) 50 else 30
        recoveryTicks = if (rage) 35 else 50
    }
    fun tick(valid: Boolean): Boolean {
        if (!valid) { reset(); return false }
        if (phase == Phase.IDLE) return false
        val before = elapsed
        elapsed += 5
        phase = when {
            elapsed < windupTicks -> Phase.WINDUP
            before < windupTicks -> Phase.CONTACT
            elapsed < windupTicks + recoveryTicks -> Phase.RECOVERY
            else -> Phase.IDLE
        }
        return phase == Phase.CONTACT
    }
    fun reset() { phase = Phase.IDLE; elapsed = 0; swallowing = false }
}
