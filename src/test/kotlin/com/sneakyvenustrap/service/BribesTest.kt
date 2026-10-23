package com.sneakyvenustrap.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.Random
import java.util.UUID

class BribesTest {
    @Test fun exactlyOneOfFiveOutcomesGrantsTemporaryTruceWithNoSpamRolls() {
        for (roll in 0..4) {
            var calls = 0
            val random = object : Random() { override fun nextInt(bound: Int): Int { assertEquals(5, bound); calls++; return roll } }
            val bribes = Bribes(random, 60, 20)
            val id = UUID.randomUUID()
            assertEquals(if (roll == 0) Bribes.Result.TRUCE else Bribes.Result.REJECTED, bribes.offer(id, 100))
            assertEquals(Bribes.Result.COOLDOWN, bribes.offer(id, 101))
            assertEquals(1, calls)
            assertEquals(roll == 0, bribes.truce(id, 110))
            assertFalse(bribes.truce(id, 120))
            assertNotEquals(Bribes.Result.COOLDOWN, bribes.offer(id, 160))
        }
    }
}
