package dev.johnrish.pocketengineer.network

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

sealed interface CancellableHttpStreamEvent {
    data class ResponseStarted(val code: Int, val contentType: String?) : CancellableHttpStreamEvent
    data class ServerEvent(val event: ServerSentEvent) : CancellableHttpStreamEvent
    data class RawBody(val body: String) : CancellableHttpStreamEvent
}

/**
 * Streams SSE events without buffering the full completion in memory.
 *
 * The OkHttp callback thread uses a bounded, back-pressured channel: it waits for the collector
 * rather than dropping deltas or accumulating an unbounded queue. Cancelling collection cancels
 * the underlying Call and closes the active response.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun Call.Factory.executeCancellableStream(
    request: Request,
    maxBodyBytes: Int
): Flow<CancellableHttpStreamEvent> {
    require(maxBodyBytes > 0) { "Response limit must be positive." }

    return callbackFlow {
        val producer = this
        val call = newCall(request)

        fun emitBlocking(event: CancellableHttpStreamEvent): Boolean = try {
            runBlocking { producer.send(event) }
            true
        } catch (_: Exception) {
            false
        }

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                producer.close(error)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    response.use { active ->
                        val body = active.body
                        val contentType = body?.contentType()?.toString()
                        if (!emitBlocking(CancellableHttpStreamEvent.ResponseStarted(active.code, contentType))) {
                            call.cancel()
                            return
                        }
                        if (body == null) {
                            producer.close()
                            return
                        }
                        if (body.contentLength() > maxBodyBytes) {
                            throw IOException("Response exceeds the configured ${maxBodyBytes}-byte limit.")
                        }

                        val isEventStream = body.contentType()?.let {
                            it.type.equals("text", ignoreCase = true) &&
                                it.subtype.equals("event-stream", ignoreCase = true)
                        } == true

                        if (!active.isSuccessful || !isEventStream) {
                            val raw = readBoundedBody(body.byteStream(), maxBodyBytes)
                            if (!emitBlocking(CancellableHttpStreamEvent.RawBody(raw))) {
                                call.cancel()
                                return
                            }
                            producer.close()
                            return
                        }

                        val parser = ServerSentEventParser()
                        val boundedInput = BoundedInputStream(body.byteStream(), maxBodyBytes)
                        val decoder = StandardCharsets.UTF_8.newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                        val reader = InputStreamReader(boundedInput, decoder)
                        val buffer = CharArray(4096)

                        while (true) {
                            val count = reader.read(buffer)
                            if (count < 0) break
                            val events = parser.feed(String(buffer, 0, count))
                            for (event in events) {
                                if (!emitBlocking(CancellableHttpStreamEvent.ServerEvent(event))) {
                                    call.cancel()
                                    return
                                }
                            }
                        }
                        // Deliberately discard an unterminated final SSE event.
                        parser.finish()
                        producer.close()
                    }
                } catch (error: Exception) {
                    producer.close(error)
                }
            }
        })

        awaitClose { call.cancel() }
    }.buffer(Channel.BUFFERED)
}

private fun readBoundedBody(input: InputStream, maxBytes: Int): String {
    BoundedInputStream(input, maxBytes).use { bounded ->
        val output = ByteArrayOutputStream(minOf(maxBytes, 8 * 1024))
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val count = bounded.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            output.write(buffer, 0, count)
        }
        return output.toString(StandardCharsets.UTF_8.name())
    }
}

private class BoundedInputStream(
    input: InputStream,
    private val maxBytes: Int
) : FilterInputStream(input) {
    private var totalBytes = 0

    override fun read(): Int {
        val value = super.read()
        if (value >= 0) account(1)
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val remaining = (maxBytes - totalBytes).coerceAtLeast(0)
        val requested = minOf(length, remaining + 1)
        val count = super.read(buffer, offset, requested)
        if (count > 0) account(count)
        return count
    }

    private fun account(count: Int) {
        if (count > maxBytes - totalBytes) {
            throw IOException("Response exceeds the configured ${maxBytes}-byte limit.")
        }
        totalBytes += count
    }
}
