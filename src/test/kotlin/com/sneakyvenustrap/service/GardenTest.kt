package com.sneakyvenustrap.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GardenTest {
    @Test fun runningAndMicrophoneAttractMoreThanSneakingButSneakingStillCounts() {
        assertTrue(Noise.score(true, false, false) > Noise.score(false, false, false))
        assertTrue(Noise.score(false, false, true) > Noise.score(false, false, false))
        assertTrue(Noise.score(false, true, false) > 0)
        assertTrue(Noise.score(false, true, false) < Noise.score(false, false, false))
    }

    @Test fun mouthsShareHealthOnlyDuringExposedWindow() {
        val garden = Garden(100.0)
        assertFalse(garden.hit(10.0))
        garden.expose(40)
        assertTrue(garden.hit(30.0))
        assertEquals(70.0, garden.health)
        repeat(40) { garden.tick() }
        assertFalse(garden.hit(30.0))
        assertEquals(70.0, garden.health)
        garden.expose(20)
        assertTrue(garden.hit(1000.0))
        assertTrue(garden.dead)
        assertFalse(garden.hit(1.0))
    }
}
