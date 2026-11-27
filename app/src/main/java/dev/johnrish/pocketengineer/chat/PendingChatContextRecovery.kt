package dev.johnrish.pocketengineer.chat

/**
 * Restores a submitted file attachment after a stopped request only when the user has not
 * selected a replacement attachment while the request was running.
 */
data class PendingChatContext(val name: String, val content: String)

object PendingChatContextRecovery {
    fun restoreIfNoReplacement(
        selectionChangedDuringRequest: Boolean,
        currentName: String?,
        currentContent: String?,
        submittedName: String?,
        submittedContent: String?
    ): PendingChatContext? {
        if (selectionChangedDuringRequest || currentName != null || currentContent != null) return null
        val name = submittedName?.takeIf(String::isNotBlank) ?: return null
        val content = submittedContent ?: return null
        return PendingChatContext(name, content)
    }
}
