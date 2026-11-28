package dev.johnrish.pocketengineer.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ForegroundRetryPolicyTest {
    @Test
    fun retriesOnlyTransientStatusesWithinTheAttemptBudget() {
        listOf(408, 425, 429, 500, 502, 503, 504, 599).forEach { status ->
            assertTrue("Expected HTTP $status to be retryable", ForegroundRetryPolicy.shouldRetryHttpStatus(0, status))
        }
        assertFalse(ForegroundRetryPolicy.shouldRetryHttpStatus(2, 503))
        assertFalse(ForegroundRetryPolicy.shouldRetryHttpStatus(-1, 503))
        assertFalse(ForegroundRetryPolicy.shouldRetryHttpStatus(0, 400))
        assertFalse(ForegroundRetryPolicy.shouldRetryHttpStatus(0, 401))
        assertFalse(ForegroundRetryPolicy.shouldRetryHttpStatus(0, 404))
    }

    @Test
    fun doesNotRetryRateLimitsThatRepresentExhaustedQuota() {
        assertFalse(ForegroundRetryPolicy.isRetryableStatus(429, "insufficient_quota"))
        assertFalse(ForegroundRetryPolicy.isRetryableStatus(429, "billing_hard_limit_reached"))
        assertTrue(ForegroundRetryPolicy.isRetryableStatus(429, "rate_limit_exceeded"))
        assertTrue(ForegroundRetryPolicy.isRetryableStatus(429, null))
    }

    @Test
    fun honorsRetryAfterDeltaSecondsAndRefusesLongAutomaticWaits() {
        assertEquals(5_000L, ForegroundRetryPolicy.delayMillis(0, "5", 0L))
        assertEquals(0L, ForegroundRetryPolicy.delayMillis(0, "0", 0L))
        assertFalse(ForegroundRetryPolicy.shouldRetryHttpStatus(0, 429, "31", nowEpochMillis = 0L))
        assertTrue(ForegroundRetryPolicy.retryAfterExceedsAutomaticLimit("31", 0L))
    }

    @Test
    fun parsesRetryAfterHttpDate() {
        val now = Instant.parse("2015-10-21T07:27:00Z").toEpochMilli()
        assertEquals(
            5_000L,
            ForegroundRetryPolicy.delayMillis(0, "Wed, 21 Oct 2015 07:27:05 GMT", now)
        )
    }

    @Test
    fun malformedOrMissingRetryAfterUsesBoundedExponentialBackoff() {
        assertEquals(750L, ForegroundRetryPolicy.delayMillis(0, "not-a-date", 0L))
        assertEquals(1_500L, ForegroundRetryPolicy.delayMillis(1, null, 0L))
        assertEquals(3_000L, ForegroundRetryPolicy.delayMillis(2, null, 0L))
        assertEquals(6_000L, ForegroundRetryPolicy.delayMillis(9, null, 0L))
    }

    @Test
    fun rejectsNegativeRetryIndex() {
        assertFalse(ForegroundRetryPolicy.shouldRetryNetworkFailure(-1))
    }
}
