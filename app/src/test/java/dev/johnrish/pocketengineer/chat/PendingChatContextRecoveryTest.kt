package dev.johnrish.pocketengineer.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PendingChatContextRecoveryTest {
    @Test
    fun restoresSubmittedAttachmentWhenNoReplacementExists() {
        val restored = PendingChatContextRecovery.restoreIfNoReplacement(
            currentName = null,
            currentContent = null,
            submittedName = "MainActivity.kt",
            submittedContent = "class MainActivity"
        )

        assertEquals(PendingChatContext("MainActivity.kt", "class MainActivity"), restored)
    }

    @Test
    fun preservesAttachmentSelectedWhileRequestWasRunning() {
        val restored = PendingChatContextRecovery.restoreIfNoReplacement(
            currentName = "README.md",
            currentContent = "new selection",
            submittedName = "MainActivity.kt",
            submittedContent = "original selection"
        )

        assertNull(restored)
    }

    @Test
    fun doesNotRestoreIncompleteSubmittedContext() {
        assertNull(
            PendingChatContextRecovery.restoreIfNoReplacement(
                currentName = null,
                currentContent = null,
                submittedName = "MainActivity.kt",
                submittedContent = null
            )
        )
        assertNull(
            PendingChatContextRecovery.restoreIfNoReplacement(
                currentName = null,
                currentContent = null,
                submittedName = " ",
                submittedContent = "content"
            )
        )
    }

    @Test
    fun doesNotOverwritePartiallyPresentCurrentContext() {
        assertNull(
            PendingChatContextRecovery.restoreIfNoReplacement(
                currentName = null,
                currentContent = "new content",
                submittedName = "MainActivity.kt",
                submittedContent = "original content"
            )
        )
    }
}
