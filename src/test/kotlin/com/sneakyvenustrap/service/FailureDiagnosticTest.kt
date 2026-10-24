package com.sneakyvenustrap.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FailureDiagnosticTest {
    @Test fun configurationReasonSurvivesButArbitraryPayloadDoesNot() {
        val known = FailureDiagnostic.describe(IllegalArgumentException("Refusing unowned world sneakyvenustrap_stomach; choose another world name manually before retrying."))
        assertTrue(known.contains("IllegalArgumentException"))
        assertTrue(known.contains("Refusing unowned world"))
        assertTrue(known.contains("choose another world name"))
        val unknown = FailureDiagnostic.describe(IllegalStateException("password=secret\nBearer private-token"))
        assertEquals("IllegalStateException: details withheld", unknown)
        assertTrue(known.length <= 200)
    }
}
