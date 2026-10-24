package com.sneakyvenustrap.encounter

import org.bukkit.Location
import org.bukkit.entity.Display
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.cos
import kotlin.math.sin

/** Three mouthless rooted vines: fixed entity budget, continuous segmented curves, no hazards. */
internal class RootVines(origin: Location, spawn: (Location) -> Display) {
    private val vines = List(3) { vine ->
        val angle = vine * Math.PI * 2 / 3
        val base = origin.clone().add(1.5 + cos(angle) * 2.5, 0.05, 1.5 + sin(angle) * 2.5)
        List(7) { spawn(base) }
    }
    init { pose(0f, 0f) }

    fun pose(seconds: Float, stretch: Float) {
        vines.forEachIndexed { vine, parts ->
            val angle = vine * Math.PI * 2 / 3
            fun point(i: Int): Vector3f {
                val t = i / 7f
                val bend = sin(t * Math.PI * 0.8 + seconds * 0.8 + vine) * t * (1.0 + stretch)
                return Vector3f((cos(angle) * bend).toFloat(),
                    t * (3.5f + stretch) + (sin(seconds * 0.9 + vine) * t * 0.3).toFloat(),
                    (sin(angle) * bend).toFloat())
            }
            parts.forEachIndexed { i, part ->
                val a = point(i); val d = point(i + 1).sub(a); val length = d.length()
                part.interpolationDelay = 0
                part.interpolationDuration = 5
                part.transformation = Transformation(a,
                    Quaternionf().rotationTo(Vector3f(0f, 1f, 0f), d.div(length)),
                    Vector3f(1.8f - i * 0.12f, length * 16f / 10.2f, 1.8f - i * 0.12f), Quaternionf())
            }
        }
    }
}
