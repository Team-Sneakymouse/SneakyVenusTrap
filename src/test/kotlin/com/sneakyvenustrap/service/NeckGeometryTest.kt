package com.sneakyvenustrap.service

import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NeckGeometryTest {
    @Test fun sixConnectorsReachTheSharedHingeAcrossExtremePoses() {
        for (yaw in listOf(-3f, 0f, 2f)) for (recoil in listOf(-1f, 0f, 1.5f)) {
            val pose = NeckGeometry.pose(Vector3f(4f, -2f, -6f), yaw, recoil)
            assertEquals(6, pose.segments.size)
            var previous = pose.attachment
            for (segment in pose.segments) {
                assertTrue(previous.distance(segment.start) < 0.0001f)
                val end = segment.rotation.transform(Vector3f(0f, 10.2f / 16f * segment.lengthScale, 0f)).add(segment.start)
                assertTrue(end.distance(segment.end) < 0.0001f)
                assertTrue(end.isFinite)
                previous = end
            }
            assertTrue(previous.distance(pose.hinge) < 0.0001f)
        }
    }
}
