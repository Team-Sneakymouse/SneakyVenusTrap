package com.sneakyvenustrap.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FlytrapGeometryTest {
    @Test
    fun `open head has separate fleshy jaws and tapering teeth on both lips within budget`() {
        val head = FlytrapGeometry.head(2.4)
        assertTrue(head.size <= 200)
        for (side in listOf(-1, 1)) {
            assertTrue(head.any { it.role == "shell" && it.y * side > 0.5 })
            assertTrue(head.any { it.role == "flesh" && it.y * side > 0.2 })
            val teeth = head.filter { it.role == "tooth" && it.y * side > 0 }
            assertTrue(teeth.size >= 21)
            assertTrue(teeth.map { it.sx }.distinct().size >= 3, "Teeth must taper, not be pillars")
            assertTrue(teeth.all { it.z < 0 }, "Toothed lips face forward (-Z)")
        }
        assertTrue(head.any { it.role == "throat" && it.z > 0 })
        assertTrue(head.all { it.sx > 0 && it.sy > 0 && it.sz > 0 })
        val bud = FlytrapGeometry.head(0.6)
        assertEquals(head.size, bud.size)
        assertEquals(head.first().sx / 4, bud.first().sx, 0.00001)
    }
}
