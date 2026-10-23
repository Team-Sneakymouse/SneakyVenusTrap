package com.sneakyvenustrap.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.Random

class DigestionTest {
    @Test fun originalCryBreaksKnotsWhileEchoesRaiseAcidAndAlliesBuyBoundedTime() {
        val stomach = Digestion(Random(7), 3, 100)
        val original = stomach.original
        assertEquals(Digestion.Hit.ECHO, stomach.hit((original + 1) % 4))
        assertEquals(20, stomach.pressure)
        stomach.help()
        assertEquals(10, stomach.pressure)
        repeat(3) { assertEquals(if (it == 2) Digestion.Hit.ESCAPED else Digestion.Hit.KNOT, stomach.hit(stomach.original)) }
        assertTrue(stomach.escaped)
        assertFalse(stomach.expired)
    }
}
