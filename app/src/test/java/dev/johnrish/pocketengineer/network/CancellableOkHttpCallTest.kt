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
import okio.Timeout
import okio.Source
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertTrue("Late callback response must be closed.", lateBody.closed.get())
    }

    @Test
    fun cancellationDuringBodyReadClosesTheResponseStream() = runBlocking {
        val request = request()
        val call = RecordingCall(request)
        val body = BlockingBody()
        val pending = launch { factory(call).executeCancellable(request, maxBodyBytes = 32) }

        assertTrue(call.enqueued.await(2, TimeUnit.SECONDS))
        val callbackThread = Thread {
            call.callback!!.onResponse(call, response(request, body))
        }.apply { isDaemon = true }
        callbackThread.start()

        assertTrue(body.readStarted.await(2, TimeUnit.SECONDS))
        pending.cancelAndJoin()
        callbackThread.join(2_000)

        assertTrue(call.cancelled)
        assertTrue(body.closed.get())
        assertFalse(callbackThread.isAlive)
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

    private class BlockingBody : ResponseBody() {
        val readStarted = CountDownLatch(1)
        val closed = AtomicBoolean(false)
        private val releaseRead = CountDownLatch(1)
        private val bufferedSource: BufferedSource = object : Source {
            override fun read(sink: Buffer, byteCount: Long): Long {
                if (byteCount == 0L) return 0
                readStarted.countDown()
                releaseRead.await(2, TimeUnit.SECONDS)
                if (closed.get()) throw java.io.IOException("Response body was closed.")
                val bytes = "late".toByteArray(Charsets.UTF_8)
                val count = minOf(byteCount, bytes.size.toLong()).toInt()
                sink.write(bytes, 0, count)
                return count.toLong()
            }

            override fun timeout(): Timeout = Timeout.NONE

            override fun close() {
                closed.set(true)
                releaseRead.countDown()
            }
        }.buffer()

        override fun contentType(): MediaType? = null
        override fun contentLength(): Long = -1
        override fun source(): BufferedSource = bufferedSource

        override fun close() {
            closed.set(true)
            super.close()
        }
    }

    private class TrackingBody(private val value: String) : ResponseBody() {
        val closed = AtomicBoolean(false)
        private val bufferedSource = Buffer().writeUtf8(value)

        override fun contentType(): MediaType? = null
        override fun contentLength(): Long = value.toByteArray(Charsets.UTF_8).size.toLong()
        override fun source(): BufferedSource = bufferedSource

        override fun close() {
            closed.set(true)
            super.close()
        }
    }
}
