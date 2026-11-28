package dev.johnrish.pocketengineer.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class OpenAiChatStreamAccumulatorTest {
    @Test
    fun accumulatesVisibleTextAndRecognizesDoneMarker() {
        val accumulator = OpenAiChatStreamAccumulator()

        assertEquals("Hello", accumulator.accept(ServerSentEvent(
            """{"choices":[{"delta":{"content":"Hello"}}]}""",
            "message",
            null
        )))
        assertEquals(" world", accumulator.accept(ServerSentEvent(
            """{"choices":[{"delta":{"content":" world"}}]}""",
            "message",
            null
        )))
        assertNull(accumulator.accept(ServerSentEvent("[DONE]", "message", null)))
        assertTrue(accumulator.isDone)
        assertEquals("Hello world", accumulator.toAssistantMessage().optString("content"))
    }

    @Test
    fun reconstructsFragmentedToolNameAndArgumentsInIndexOrder() {
        val accumulator = OpenAiChatStreamAccumulator()
        accumulator.accept(ServerSentEvent(
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","type":"function","function":{"name":"workspace.read_","arguments":"{\"path\":"}}]}}]}""",
            "message",
            null
        ))
        accumulator.accept(ServerSentEvent(
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"name":"file","arguments":"\"README.md\"}"}}]}}]}""",
            "message",
            null
        ))

        val call = accumulator.toAssistantMessage().getJSONArray("tool_calls").getJSONObject(0)
        assertEquals("call_1", call.optString("id"))
        assertEquals("workspace.read_file", call.getJSONObject("function").optString("name"))
        assertEquals("""{"path":"README.md"}""", call.getJSONObject("function").optString("arguments"))
        assertFalse(accumulator.contentText.isNotBlank())
    }

    @Test
    fun ignoresUsageOnlyChunksAndEmptyChoices() {
        val accumulator = OpenAiChatStreamAccumulator()
        assertNull(accumulator.accept(ServerSentEvent(
            """{"choices":[],"usage":{"total_tokens":10}}""",
            "message",
            null
        )))
        assertNull(accumulator.accept(ServerSentEvent(
            """{"choices":[{"delta":{}}]}""",
            "message",
            null
        )))
        assertFalse(accumulator.hasToolCalls)
    }

    @Test
    fun rejectsMalformedAndProviderErrorEvents() {
        val accumulator = OpenAiChatStreamAccumulator()
        assertThrows(IllegalArgumentException::class.java) {
            accumulator.accept(ServerSentEvent("{not-json}", "message", null))
        }
        assertThrows(IllegalStateException::class.java) {
            accumulator.accept(ServerSentEvent(
                """{"error":{"message":"rate limited"}}""",
                "message",
                null
            ))
        }
        assertThrows(IllegalStateException::class.java) {
            accumulator.accept(ServerSentEvent("""{"message":"failed"}""", "error", null))
        }
    }

    @Test
    fun enforcesContentToolArgumentAndToolIndexLimits() {
        val contentLimited = OpenAiChatStreamAccumulator(maxContentChars = 3)
        assertThrows(IllegalArgumentException::class.java) {
            contentLimited.accept(ServerSentEvent(
                """{"choices":[{"delta":{"content":"four"}}]}""",
                "message",
                null
            ))
        }

        val argumentsLimited = OpenAiChatStreamAccumulator(maxToolArgumentChars = 4)
        assertThrows(IllegalArgumentException::class.java) {
            argumentsLimited.accept(ServerSentEvent(
                """{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"12345"}}]}}]}""",
                "message",
                null
            ))
        }

        val indexLimited = OpenAiChatStreamAccumulator(maxToolCalls = 1)
        assertThrows(IllegalArgumentException::class.java) {
            indexLimited.accept(ServerSentEvent(
                """{"choices":[{"delta":{"tool_calls":[{"index":1,"function":{"name":"tool"}}]}}]}""",
                "message",
                null
            ))
        }
    }

    @Test
    fun rejectsChangedToolCallIdsAcrossFragments() {
        val accumulator = OpenAiChatStreamAccumulator()
        accumulator.accept(ServerSentEvent(
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_a"}]}}]}""",
            "message",
            null
        ))
        assertThrows(IllegalArgumentException::class.java) {
            accumulator.accept(ServerSentEvent(
                """{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_b"}]}}]}""",
                "message",
                null
            ))
        }
    }

    @Test
    fun rejectsUnsupportedToolCallTypesAndContentValues() {
        val wrongType = OpenAiChatStreamAccumulator()
        assertThrows(IllegalArgumentException::class.java) {
            wrongType.accept(ServerSentEvent(
                """{"choices":[{"delta":{"tool_calls":[{"index":0,"type":"custom"}]}}]}""",
                "message",
                null
            ))
        }

        val wrongContent = OpenAiChatStreamAccumulator()
        assertThrows(IllegalArgumentException::class.java) {
            wrongContent.accept(ServerSentEvent(
                """{"choices":[{"delta":{"content":{"text":"not a string"}}}]}""",
                "message",
                null
            ))
        }
    }
}
