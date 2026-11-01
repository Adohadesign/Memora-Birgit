package de.adohadesign.memorasolo.data

import org.junit.Assert.*
import org.junit.Test

class EntryPolicyTest {
    @Test fun shortInterruptionKeepsCard() {
        assertFalse(EntryPolicy.shouldClose(1_000, 600_999))
    }
    @Test fun exactlyTenMinutesClosesCard() {
        assertTrue(EntryPolicy.shouldClose(1_000, 601_000))
    }
    @Test fun interruptedClockIsSafe() {
        assertTrue(EntryPolicy.shouldClose(5_000, 4_000))
    }
    @Test fun afterMidnightBelongsToStartDay() {
        assertEquals("2026-10-31", EntryPolicy.assignedDate("2026-10-31", "2026-11-01"))
    }
}
