package dev.johnrish.pocketengineer.network

import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.Timeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class CancellableOkHttpCallTest {
    @Test
    fun successfulResponseIsBoundedAndClosed() = runBlocking {
        val request = request()
        val call = RecordingCall(request)
        val body = TrackingBody("ready")
        val pending = async { factory(call).executeCancellable(request, maxBodyBytes = 32) }

        assertTrue(call.enqueued.await(2, TimeUnit.SECONDS))
        call.callback!!.onResponse(call, response(request, body))

        val result = pending.await()
        assertEquals(200, result.code)
        assertEquals("ready", result.body)
        assertTrue(body.closed.get())
    }

    @Test
    fun cancellationCancelsCallAndClosesLateResponse() = runBlocking {
        val request = request()
        val call = RecordingCall(request)
        val pending = launch { factory(call).executeCancellable(request, maxBodyBytes = 32) }

        assertTrue(call.enqueued.await(2, TimeUnit.SECONDS))
        pending.cancelAndJoin()
        assertTrue(call.cancelled)

        val lateBody = TrackingBody("late")
        call.callback!!.onResponse(call, response(request, lateBody))
        assertTrue(lateBody.closed.get())
    }

    @Test
    fun responseLargerThanLimitIsRejectedAndClosed() = runBlocking {
        val request = request()
        val call = RecordingCall(request)
        val body = TrackingBody("too large")
        val pending = async {
            runCatching { factory(call).executeCancellable(request, maxBodyBytes = 2) }
        }

        assertTrue(call.enqueued.await(2, TimeUnit.SECONDS))
        call.callback!!.onResponse(call, response(request, body))

        val result = pending.await()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertTrue(body.closed.get())
    }

    private fun factory(call: Call): Call.Factory = object : Call.Factory {
        override fun newCall(request: Request): Call = call
    }

    private fun request(): Request = Request.Builder()
        .url("https://example.test/chat")
        .build()

    private fun response(request: Request, body: ResponseBody): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body)
            .build()

    private class RecordingCall(private val originalRequest: Request) : Call {
        val enqueued = CountDownLatch(1)
        @Volatile var callback: Callback? = null
        @Volatile var cancelled = false

        override fun request(): Request = originalRequest
        override fun execute(): Response = error("Synchronous execution is not used in this test.")
        override fun enqueue(responseCallback: Callback) {
            callback = responseCallback
            enqueued.countDown()
        }
        override fun cancel() {
            cancelled = true
        }
        override fun isExecuted(): Boolean = callback != null
        override fun isCanceled(): Boolean = cancelled
        override fun timeout(): Timeout = Timeout.NONE
        override fun clone(): Call = RecordingCall(originalRequest)
    }

    private class TrackingBody(private val value: String) : ResponseBody() {
        val closed = AtomicBoolean(false)
        private val bufferedSource: BufferedSource =
            object : ForwardingSource(Buffer().writeUtf8(value)) {
                override fun close() {
                    closed.set(true)
                    super.close()
                }
            }.buffer()

        override fun contentType(): MediaType? = null
        override fun contentLength(): Long = value.toByteArray(Charsets.UTF_8).size.toLong()
        override fun source(): BufferedSource = bufferedSource
    }
}
