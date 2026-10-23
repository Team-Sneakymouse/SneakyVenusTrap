package com.sneakyvenustrap.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class MicrophoneActivityTest {
    @Test fun onlyOnlineAdmittedSpeakersAttractBrieflyAndClosedCallbacksAreInert() {
        var now = 100L
        val activity = MicrophoneActivity { now }
        val id = UUID.randomUUID()
        activity.transmitted(id)
        assertFalse(activity.active(id))
        activity.admit(setOf(id))
        activity.transmitted(id)
        assertTrue(activity.active(id))
        now += 1_000_000_001L
        assertFalse(activity.active(id))
        activity.transmitted(id)
        activity.admit(emptySet())
        assertFalse(activity.active(id))
        activity.close()
        activity.admit(setOf(id))
        activity.transmitted(id)
        assertFalse(activity.active(id))
    }
}
