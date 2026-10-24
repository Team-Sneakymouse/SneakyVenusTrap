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
    /** Track through early windup, then give a fixed ten-tick dodge window. */
    val tracking get() = phase == Phase.WINDUP && elapsed < windupTicks - 10
    val closedHoldTicks get() = if (swallowing) 25 else 15
    val progress get() = when (phase) {
        Phase.IDLE -> 0f
        Phase.WINDUP -> if (elapsed >= windupTicks - 5) 1f else elapsed.toFloat() / windupTicks * 0.35f
        else -> (1f - (elapsed - windupTicks - closedHoldTicks).coerceAtLeast(0).toFloat() /
            (recoveryTicks - closedHoldTicks)).coerceIn(0f, 1f)
    }
    val closure get() = when (phase) {
        Phase.WINDUP -> if (elapsed >= windupTicks - 5) 1f else 0f
        Phase.IDLE -> 1f
        else -> progress
    }
    fun start(swallow: Boolean = false, rage: Boolean = false) {
        phase = Phase.WINDUP; elapsed = 0; swallowing = swallow
        windupTicks = if (swallow) 50 else 30
        recoveryTicks = if (rage) 35 else 50
    }
    fun tick(valid: Boolean): Boolean {
        // A committed bite owns its recovery, not the victim's new exterior eligibility.
        if (!valid && phase == Phase.WINDUP) { reset(); return false }
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
