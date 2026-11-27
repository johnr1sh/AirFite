package dev.johnrish.pocketengineer.network

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class CancellableHttpResponse(
    val code: Int,
    val body: String
) {
    val isSuccessful: Boolean
        get() = code in 200..299
}

/**
 * Executes an OkHttp call without blocking a coroutine thread.
 *
 * Cancellation is connected to the underlying Call, including while the response body is being
 * read. The response is consumed on OkHttp's callback dispatcher, bounded, and always closed
 * before its text is delivered to the caller.
 */
suspend fun Call.Factory.executeCancellable(
    request: Request,
    maxBodyBytes: Int
): CancellableHttpResponse {
    require(maxBodyBytes > 0) { "Response limit must be positive." }

    return suspendCancellableCoroutine { continuation ->
        val call = newCall(request)
        val responseRef = AtomicReference<Response?>()

        continuation.invokeOnCancellation {
            call.cancel()
            responseRef.getAndSet(null)?.let { response ->
                runCatching { response.close() }
            }
        }

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) {
                    runCatching { continuation.resumeWithException(e) }
                }
            }

            override fun onResponse(call: Call, response: Response) {
                responseRef.set(response)
                if (!continuation.isActive) {
                    responseRef.getAndSet(null)?.let { runCatching { it.close() } }
                    return
                }

                val result = try {
                    response.use { activeResponse ->
                        CancellableHttpResponse(
                            code = activeResponse.code,
                            body = readBoundedBody(activeResponse, maxBodyBytes)
                        )
                    }
                } catch (error: Exception) {
                    responseRef.compareAndSet(response, null)
                    if (continuation.isActive) {
                        runCatching { continuation.resumeWithException(error) }
                    }
                    return
                }

                responseRef.compareAndSet(response, null)
                if (continuation.isActive) {
                    runCatching { continuation.resume(result) }
                }
            }
        })
    }
}

private fun readBoundedBody(response: Response, maxBytes: Int): String {
    val body = response.body ?: return ""
    if (body.contentLength() > maxBytes) {
        throw IllegalStateException("Response exceeds the configured ${maxBytes}-byte limit.")
    }

    body.byteStream().use { input ->
        val output = ByteArrayOutputStream(minOf(maxBytes, 8 * 1024))
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            if (count > maxBytes - total) {
                throw IllegalStateException("Response exceeds the configured ${maxBytes}-byte limit.")
            }
            output.write(buffer, 0, count)
            total += count
        }
        return output.toString(Charsets.UTF_8.name())
    }
}
