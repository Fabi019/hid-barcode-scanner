package dev.fabik.bluetoothhid.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentCodesTest {

    @Test
    fun testNewCodesAreReported() {
        val codes = RecentCodes()
        assertEquals(setOf("A", "B"), codes.markSeen(listOf("A", "B"), now = 0, graceMs = 1000))
    }

    @Test
    fun testCodeStillInViewIsNotReportedAgain() {
        val codes = RecentCodes()
        codes.markSeen(listOf("A"), now = 0, graceMs = 1000)
        assertEquals(emptySet<String>(), codes.markSeen(listOf("A"), now = 100, graceMs = 1000))
        assertEquals(setOf("B"), codes.markSeen(listOf("A", "B"), now = 200, graceMs = 1000))
    }

    @Test
    fun testCodeMissingBrieflyIsNotReportedAgain() {
        val codes = RecentCodes()
        codes.markSeen(listOf("A"), now = 0, graceMs = 1000)
        codes.expire(now = 300, graceMs = 1000)
        assertEquals(emptySet<String>(), codes.markSeen(listOf("A"), now = 900, graceMs = 1000))
    }

    @Test
    fun testCodeIsReportedAgainAfterGracePeriod() {
        val codes = RecentCodes()
        codes.markSeen(listOf("A"), now = 0, graceMs = 1000)
        codes.expire(now = 1500, graceMs = 1000)
        assertEquals(setOf("A"), codes.markSeen(listOf("A"), now = 1600, graceMs = 1000))
    }

    @Test
    fun testGracePeriodRunsFromLastSighting() {
        val codes = RecentCodes()
        codes.markSeen(listOf("A"), now = 0, graceMs = 1000)
        codes.markSeen(listOf("A"), now = 900, graceMs = 1000)
        assertEquals(emptySet<String>(), codes.markSeen(listOf("A"), now = 1800, graceMs = 1000))
    }

    @Test
    fun testClearForgetsEverything() {
        val codes = RecentCodes()
        codes.markSeen(listOf("A"), now = 0, graceMs = 1000)
        codes.clear()
        assertEquals(setOf("A"), codes.markSeen(listOf("A"), now = 100, graceMs = 1000))
    }
}
