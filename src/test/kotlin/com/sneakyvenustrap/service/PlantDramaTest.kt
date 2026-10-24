package com.sneakyvenustrap.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PlantDramaTest {
    @Test fun ordinaryBundleContactConsumesOneSlotAndKeepsFullStagger() {
        val drama = PlantDrama()
        assertFalse(drama.absorb(0, true))
        assertTrue(drama.absorb(0, false))
        assertFalse(drama.bundleAvailable(0))
        repeat(11) { drama.tick(0.4) }
        assertEquals(5, drama.staggerTicks)
        assertTrue(drama.raging)
        drama.tick(0.9)
        assertEquals(0, drama.staggerTicks)
        assertTrue(drama.raging)
        assertFalse(drama.absorb(0, false))
        repeat(28) { drama.tick(1.0) }
        assertTrue(drama.bundleAvailable(0))
    }
}
