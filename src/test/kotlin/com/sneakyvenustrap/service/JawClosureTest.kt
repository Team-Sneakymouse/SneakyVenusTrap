package com.sneakyvenustrap.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.cos
import kotlin.math.sin

class JawClosureTest {
    private data class Box(val jaw: String, val name: String, val bounds: List<Double>)
    private val boxes = javaClass.getResourceAsStream("/canonical-jaw-bounds.csv")!!.bufferedReader().useLines { lines ->
        lines.filter { !it.startsWith("#") && it.isNotBlank() }.map {
            val row = it.split(','); Box(row[0], row[1], row.drop(2).map(String::toDouble))
        }.toList()
    }
    private fun rect(box: Box, degrees: Double): List<Pair<Double, Double>> {
        val r = Math.toRadians(degrees); val c = cos(r); val s = sin(r)
        return listOf(box.bounds[1], box.bounds[4]).flatMap { y ->
            listOf(box.bounds[2], box.bounds[5]).map { z ->
                (c * (y - 8) - s * (z - 13)) to (s * (y - 8) + c * (z - 13))
            }
        }
    }
    /** Source cubes have zero element rotation; after shared X hinges SAT is X + four YZ axes. */
    private fun overlaps(a: Box, b: Box, angle: Double): Boolean {
        if (minOf(a.bounds[3], b.bounds[3]) - maxOf(a.bounds[0], b.bounds[0]) < 0.00001) return false
        val ra = rect(a, angle); val rb = rect(b, -angle)
        for (degrees in listOf(angle, -angle)) {
            val c = cos(Math.toRadians(degrees)); val s = sin(Math.toRadians(degrees))
            for ((x, y) in listOf(c to s, -s to c)) {
                val pa = ra.map { it.first * x + it.second * y }; val pb = rb.map { it.first * x + it.second * y }
                if (minOf(pa.max(), pb.max()) - maxOf(pa.min(), pb.min()) < 0.00001) return false
            }
        }
        return true
    }
    private fun collisions(angle: Double): Int = boxes.filter { it.jaw == "upper" }.sumOf { a ->
        boxes.count { b -> b.jaw == "lower" && ("fang" in a.name || "fang" in b.name) && overlaps(a, b, angle) }
    }
    @Test fun canonicalToothTipsCloseWithoutInterpenetratingOppositeTeethOrLip() {
        assertEquals(370, boxes.size)
        assertTrue(collisions(0.0) > 0, "negative control: zero is not a valid close target")
        assertTrue(collisions(8.0) > 0, "former bite target crossed teeth")
        assertTrue(collisions(13.0) > 0, "stop is collision-derived, not an arbitrary near-bite angle")
        assertEquals(0, collisions(JawGeometry.CLOSED_ANGLE.toDouble()))
    }
}
