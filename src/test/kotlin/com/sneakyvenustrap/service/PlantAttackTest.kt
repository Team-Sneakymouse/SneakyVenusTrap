package com.sneakyvenustrap.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PlantAttackTest {
    @Test fun idleTicksCannotInventAnAttack() {
        val attack = PlantAttack()
        repeat(40) { assertFalse(attack.tick(true)) }
        assertEquals(PlantAttack.Phase.IDLE, attack.phase)
        assertEquals(0, attack.elapsed)
    }

    @Test fun biteAndSwallowHaveDistinctDeterministicContactAndRecovery() {
        for (swallow in listOf(false, true)) {
            val attack = PlantAttack()
            attack.start(swallow)
            val windupSteps = if (swallow) 10 else 6
            repeat(windupSteps - 1) {
                assertFalse(attack.tick(true))
                assertEquals(PlantAttack.Phase.WINDUP, attack.phase)
            }
            assertTrue(attack.tick(true))
            assertEquals(PlantAttack.Phase.CONTACT, attack.phase)
            repeat(9) { assertFalse(attack.tick(true)); assertEquals(PlantAttack.Phase.RECOVERY, attack.phase) }
            assertFalse(attack.tick(true))
            assertEquals(PlantAttack.Phase.IDLE, attack.phase)
        }
    }

    @Test fun invalidTargetCancelsWithoutContact() {
        val attack = PlantAttack()
        attack.start(true)
        repeat(9) { attack.tick(true) }
        assertFalse(attack.tick(false))
        assertEquals(PlantAttack.Phase.IDLE, attack.phase)
        assertEquals(0, attack.elapsed)
    }
}
