package dev.johnrish.pocketengineer.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatHistoryPolicyTest {
    @Test
    fun keepsOnlyTheNewestFortyMessages() {
        val messages = (1..45).map { ChatHistoryEntry("user", "message-$it") }
        val result = ChatHistoryPolicy.bound(messages)

        assertEquals(40, result.size)
        assertEquals("message-6", result.first().content)
        assertEquals("message-45", result.last().content)
    }

    @Test
    fun prioritizesNewestMessagesWithinTheCharacterBudget() {
        val result = ChatHistoryPolicy.bound(
            listOf(ChatHistoryEntry("user", "older"), ChatHistoryEntry("assistant", "newest")),
            maxTotalChars = 6
        )

        assertEquals(listOf("newest"), result.map { it.content })
    }

    @Test
    fun marksAnOversizedMessageWhenItIsTruncated() {
        val result = ChatHistoryPolicy.bound(
            listOf(ChatHistoryEntry("assistant", "x".repeat(200))),
            maxTotalChars = 80
        )

        assertEquals(1, result.size)
        assertTrue(result.single().content.endsWith(ChatHistoryPolicy.TRUNCATION_NOTICE))
        assertTrue(result.single().content.length <= 80)
    }

    @Test
    fun neverSplitsASurrogatePair() {
        val result = ChatHistoryPolicy.bound(
            listOf(ChatHistoryEntry("user", "A😀B")),
            maxTotalChars = 2
        )

        assertEquals("A", result.single().content)
    }

    @Test
    fun supportsAnEmptyCharacterBudget() {
        assertTrue(ChatHistoryPolicy.bound(
            listOf(ChatHistoryEntry("user", "message")),
            maxTotalChars = 0
        ).isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonPositiveMessageLimit() {
        ChatHistoryPolicy.bound(emptyList(), maxMessages = 0)
    }
}
