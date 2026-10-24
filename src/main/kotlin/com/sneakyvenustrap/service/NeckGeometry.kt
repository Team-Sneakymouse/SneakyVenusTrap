package com.sneakyvenustrap.service

import org.joml.Quaternionf
import org.joml.Vector3f

/** Shared model-space stalk attachment and jaw hinge, in blocks relative to display centre. */
object NeckGeometry {
    data class Segment(val start: Vector3f, val end: Vector3f, val rotation: Quaternionf, val lengthScale: Float)
    data class Pose(val attachment: Vector3f, val hinge: Vector3f, val segments: List<Segment>)

    fun pose(delta: Vector3f, yaw: Float, recoil: Float): Pose {
        val attachment = Quaternionf().rotateZ(recoil * 0.12f).transform(Vector3f(-0.0956f, 2.61f, 0f))
        val hinge = Quaternionf().rotateY(yaw).transform(Vector3f(0f, 3.01f, 0.625f))
            .add(delta).add(0f, recoil * 0.25f, recoil * 0.6f)
        val points = (0..6).map { i ->
            val t = i / 6f
            Vector3f(attachment).lerp(hinge, t).add(0f, 0f, -0.25f * kotlin.math.sin(Math.PI * t).toFloat())
        }
        return Pose(attachment, hinge, points.zipWithNext { a, b ->
            val direction = Vector3f(b).sub(a)
            val length = direction.length()
            Segment(a, b, if (length > 0.000001f) Quaternionf().rotationTo(Vector3f(0f, 1f, 0f), direction.div(length)) else Quaternionf(),
                16f * length / 10.2f)
        })
    }
}
