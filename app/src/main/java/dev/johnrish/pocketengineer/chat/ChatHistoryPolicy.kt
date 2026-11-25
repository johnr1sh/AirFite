package dev.johnrish.pocketengineer.chat

data class ChatHistoryEntry(val role: String, val content: String)

/** Keeps persisted chat history small while favoring the newest messages. */
object ChatHistoryPolicy {
    const val MAX_MESSAGES = 40
    const val MAX_TOTAL_CHARS = 128 * 1024
    const val TRUNCATION_NOTICE = "\n\n[Earlier content omitted from saved history.]"

    fun bound(
        entries: List<ChatHistoryEntry>,
        maxMessages: Int = MAX_MESSAGES,
        maxTotalChars: Int = MAX_TOTAL_CHARS
    ): List<ChatHistoryEntry> {
        require(maxMessages > 0) { "Message limit must be positive." }
        require(maxTotalChars >= 0) { "Character limit cannot be negative." }
        if (maxTotalChars == 0) return emptyList()

        var remaining = maxTotalChars
        val newestFirst = mutableListOf<ChatHistoryEntry>()
        for (entry in entries.takeLast(maxMessages).asReversed()) {
            if (remaining == 0) break
            val content = if (entry.content.length <= remaining) {
                entry.content
            } else {
                val prefixLimit = (remaining - TRUNCATION_NOTICE.length).coerceAtLeast(0)
                val prefix = safePrefix(entry.content, prefixLimit)
                if (remaining >= TRUNCATION_NOTICE.length) prefix + TRUNCATION_NOTICE
                else safePrefix(entry.content, remaining)
            }
            if (content.isNotEmpty()) {
                newestFirst.add(entry.copy(content = content))
                remaining -= content.length
            }
        }
        return newestFirst.asReversed()
    }

    private fun safePrefix(value: String, maxChars: Int): String {
        var end = minOf(value.length, maxChars)
        if (end in 1 until value.length &&
            Character.isHighSurrogate(value[end - 1]) && Character.isLowSurrogate(value[end])) {
            end--
        }
        return value.substring(0, end)
    }
}
