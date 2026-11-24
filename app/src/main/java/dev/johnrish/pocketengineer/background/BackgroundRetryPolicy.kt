package dev.johnrish.pocketengineer.background

/** Pure policy for bounded retries of transient background-provider failures. */
object BackgroundRetryPolicy {
    fun shouldRetry(attemptCount: Int, maxRetries: Int): Boolean =
        maxRetries > 0 && attemptCount >= 0 && attemptCount < maxRetries

    fun isRetryableHttpStatus(statusCode: Int): Boolean =
        statusCode == 408 || statusCode == 425 || statusCode == 429 || statusCode in 500..599
}
