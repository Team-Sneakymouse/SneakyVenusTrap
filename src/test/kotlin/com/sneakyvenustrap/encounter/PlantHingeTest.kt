package com.sneakyvenustrap.encounter

import org.bukkit.util.Transformation
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlantHingeTest {
    private fun matrix(t: Transformation) = Matrix4f().translation(t.translation)
        .rotate(t.leftRotation).scale(t.scale).rotate(t.rightRotation)

    @Test fun authoredJawHingeStaysFixedForOpenAndClosedAnglesUnderSharedScaleAndYaw() {
        // Authored q hinge [0,0,5] is 5/16 model blocks; scale 2 means world pivot 0.625.
        val localHinge = Vector3f(0f, 0f, 5f / 16f)
        val offset = Vector3f(0f, 3.01f, 0f)
        val pivot = Vector3f(0f, 0f, 0.625f)
        val delta = Vector3f(1.4f, -0.8f, 2.1f)
        for (yaw in listOf(0f, 0.7f, -1.2f)) {
            for (degrees in listOf(26f, -22f, 8f, -8f)) {
                val transform = PlantModels.transform(offset, pivot,
                    Quaternionf().rotateX(Math.toRadians(degrees.toDouble()).toFloat()), yaw, delta)
                val actual = matrix(transform).transformPosition(Vector3f(localHinge))
                val expected = Quaternionf().rotateY(yaw).transform(Vector3f(offset).add(pivot)).add(delta)
                assertEquals(expected.x, actual.x, 0.00001f)
                assertEquals(expected.y, actual.y, 0.00001f)
                assertEquals(expected.z, actual.z, 0.00001f)
            }
        }
    }

    @Test fun identityPosePreservesUniformScaleAndExactPartOffset() {
        val offset = Vector3f(0.25f, 1.135f, 0.125f)
        val local = Vector3f(0.3f, -0.2f, 0.4f)
        val transform = PlantModels.transform(offset, Vector3f(), Quaternionf())
        val actual = matrix(transform).transformPosition(Vector3f(local))
        val expected = Vector3f(local).mul(2f).add(offset)
        assertEquals(expected.x, actual.x, 0.00001f)
        assertEquals(expected.y, actual.y, 0.00001f)
        assertEquals(expected.z, actual.z, 0.00001f)
    }
}
