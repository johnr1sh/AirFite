package dev.johnrish.pocketengineer.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class ServerSentEventParserTest {
    @Test
    fun joinsMultilineDataAndHandlesCrLfSplitAcrossChunks() {
        val parser = ServerSentEventParser()

        assertTrue(parser.feed("id: 7\r").isEmpty())
        val events = parser.feed("\nevent: message\r\ndata: first\r\ndata: second\r\n\r\n")

        assertEquals(
            listOf(ServerSentEvent("first\nsecond", "message", "7")),
            events
        )
    }

    @Test
    fun ignoresBomCommentsAndUnknownFields() {
        val parser = ServerSentEventParser()
        val events = parser.feed("\uFEFF: keepalive\ndata: hello\nunknown: ignored\n\n")

        assertEquals(listOf(ServerSentEvent("hello", "message", null)), events)
    }

    @Test
    fun supportsCarriageReturnOnlyLineEndingsAndEventNames() {
        val parser = ServerSentEventParser()
        val events = parser.feed("event: delta\rdata: token\r\r")

        assertEquals(listOf(ServerSentEvent("token", "delta", null)), events)
    }

    @Test
    fun emptyDataFieldDispatchesAnEmptyMessage() {
        val parser = ServerSentEventParser()
        assertEquals(listOf(ServerSentEvent("", "message", null)), parser.feed("data\n\n"))
    }

    @Test
    fun discardsAnUnterminatedEventAtEndOfStream() {
        val parser = ServerSentEventParser()
        assertTrue(parser.feed("data: incomplete\n").isEmpty())
        assertTrue(parser.finish().isEmpty())
    }

    @Test
    fun ignoresAnIdContainingNullAndRetainsPreviousId() {
        val parser = ServerSentEventParser()
        val first = parser.feed("id: safe\ndata: one\n\n")
        val second = parser.feed("id: bad\u0000id\ndata: two\n\n")

        assertEquals("safe", first.single().id)
        assertEquals("safe", second.single().id)
    }

    @Test
    fun enforcesLineAndEventDataLimits() {
        assertThrows(IllegalArgumentException::class.java) {
            ServerSentEventParser(maxLineChars = 3).feed("data")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ServerSentEventParser(maxEventDataChars = 3).feed("data: four\n")
        }
    }
}
