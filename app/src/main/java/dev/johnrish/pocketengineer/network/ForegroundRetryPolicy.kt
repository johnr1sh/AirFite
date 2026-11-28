package dev.johnrish.pocketengineer.network

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Conservative retry rules for foreground chat requests.
 *
 * A request may be replayed only before any response headers or streamed output have arrived.
 * Provider-directed Retry-After values are honored only when they fit the short automatic-wait
 * window; longer waits are left to the user rather than retried earlier than the server requested.
 */
object ForegroundRetryPolicy {
    const val MAX_RETRIES = 2
    const val BASE_BACKOFF_MILLIS = 750L
    const val MAX_BACKOFF_MILLIS = 6_000L
    const val MAX_AUTOMATIC_RETRY_AFTER_MILLIS = 30_000L

    private val NON_RETRYABLE_RATE_LIMIT_CODES = setOf(
        "insufficient_quota",
        "billing_hard_limit_reached",
        "quota_exceeded"
    )

    fun shouldRetryNetworkFailure(retryCount: Int, maxRetries: Int = MAX_RETRIES): Boolean =
        retryCount >= 0 && maxRetries > 0 && retryCount < maxRetries

    fun isRetryableStatus(statusCode: Int, providerErrorCode: String? = null): Boolean {
        if (statusCode == 429 && providerErrorCode?.trim()?.lowercase() in NON_RETRYABLE_RATE_LIMIT_CODES) {
            return false
        }
        return statusCode == 408 || statusCode == 425 || statusCode == 429 || statusCode in 500..599
    }

    fun shouldRetryHttpStatus(
        retryCount: Int,
        statusCode: Int,
        retryAfter: String? = null,
        providerErrorCode: String? = null,
        nowEpochMillis: Long = System.currentTimeMillis(),
        maxRetries: Int = MAX_RETRIES
    ): Boolean {
        if (!shouldRetryNetworkFailure(retryCount, maxRetries) || !isRetryableStatus(statusCode, providerErrorCode)) {
            return false
        }
        val serverDelay = parseRetryAfterMillis(retryAfter, nowEpochMillis)
        return serverDelay == null || serverDelay <= MAX_AUTOMATIC_RETRY_AFTER_MILLIS
    }

    fun delayMillis(
        retryCount: Int,
        retryAfter: String? = null,
        nowEpochMillis: Long = System.currentTimeMillis()
    ): Long {
        require(retryCount >= 0) { "Retry count cannot be negative." }
        val serverDelay = parseRetryAfterMillis(retryAfter, nowEpochMillis)
        if (serverDelay != null && serverDelay <= MAX_AUTOMATIC_RETRY_AFTER_MILLIS) {
            return serverDelay
        }
        val exponent = retryCount.coerceAtMost(3)
        return (BASE_BACKOFF_MILLIS * (1L shl exponent)).coerceAtMost(MAX_BACKOFF_MILLIS)
    }

    fun retryAfterExceedsAutomaticLimit(
        retryAfter: String?,
        nowEpochMillis: Long = System.currentTimeMillis()
    ): Boolean = parseRetryAfterMillis(retryAfter, nowEpochMillis)
        ?.let { it > MAX_AUTOMATIC_RETRY_AFTER_MILLIS } == true

    private fun parseRetryAfterMillis(value: String?, nowEpochMillis: Long): Long? {
        val header = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
        if (header.all(Char::isDigit)) {
            val seconds = header.toLongOrNull() ?: return Long.MAX_VALUE
            return if (seconds > Long.MAX_VALUE / 1_000L) Long.MAX_VALUE else seconds * 1_000L
        }
        return runCatching {
            val target = ZonedDateTime.parse(header, DateTimeFormatter.RFC_1123_DATE_TIME)
                .toInstant()
                .toEpochMilli()
            (target - nowEpochMillis).coerceAtLeast(0L)
        }.getOrNull()
    }
}
