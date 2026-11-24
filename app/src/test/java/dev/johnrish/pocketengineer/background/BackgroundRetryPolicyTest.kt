package dev.johnrish.pocketengineer.background

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundRetryPolicyTest {
    @Test
    fun retriesOnlyConfiguredAttempts() {
        assertTrue(BackgroundRetryPolicy.shouldRetry(0, 3))
        assertTrue(BackgroundRetryPolicy.shouldRetry(1, 3))
        assertTrue(BackgroundRetryPolicy.shouldRetry(2, 3))
        assertFalse(BackgroundRetryPolicy.shouldRetry(3, 3))
        assertFalse(BackgroundRetryPolicy.shouldRetry(0, 0))
        assertFalse(BackgroundRetryPolicy.shouldRetry(-1, 3))
        assertFalse(BackgroundRetryPolicy.shouldRetry(1, -1))
    }

    @Test
    fun identifiesTransientProviderStatuses() {
        listOf(408, 425, 429, 500, 501, 502, 503, 504, 599).forEach { status ->
            assertTrue("Expected HTTP $status to be retryable", BackgroundRetryPolicy.isRetryableHttpStatus(status))
        }
    }

    @Test
    fun doesNotRetryPermanentOrInvalidStatuses() {
        listOf(200, 204, 400, 401, 403, 404, 422, 499, 600, -1).forEach { status ->
            assertFalse("Expected HTTP $status not to be retryable", BackgroundRetryPolicy.isRetryableHttpStatus(status))
        }
    }
}
