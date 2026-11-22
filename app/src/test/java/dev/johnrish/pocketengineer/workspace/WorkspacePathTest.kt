package dev.johnrish.pocketengineer.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class WorkspacePathTest {
    @Test
    fun acceptsSimpleAndNestedRelativePaths() {
        assertEquals("README.md", WorkspacePath.parse("README.md").value)
        val nested = WorkspacePath.parse("app/src/main/Main.kt")
        assertEquals(listOf("app", "src", "main", "Main.kt"), nested.segments)
    }

    @Test
    fun acceptsUnusualButLegalNames() {
        assertNotNull(WorkspacePath.parseOrNull("..."))
        assertNotNull(WorkspacePath.parseOrNull("a/..b/c..d"))
        assertNotNull(WorkspacePath.parseOrNull("docs/ünïcode 文件.txt"))
        assertNotNull(WorkspacePath.parseOrNull("%2e%2e/literal-name"))
    }

    @Test
    fun rejectsTraversal() {
        listOf("..", "../x", "a/../b", "a/b/..", "./a", "a/./b", ".").forEach {
            assertNull("should reject '$it'", WorkspacePath.parseOrNull(it))
        }
    }

    @Test
    fun rejectsAbsoluteAndUriStylePaths() {
        listOf("/etc/passwd", "/", "content://com.android.externalstorage.documents/tree/x", "//host/share")
            .forEach { assertNull("should reject '$it'", WorkspacePath.parseOrNull(it)) }
    }

    @Test
    fun rejectsEmptySegmentsAndTrailingSlash() {
        listOf("a//b", "a/", "a/b/").forEach { assertNull("should reject '$it'", WorkspacePath.parseOrNull(it)) }
    }

    @Test
    fun rejectsBackslashSeparatorsThatCouldHideTraversal() {
        assertNull(WorkspacePath.parseOrNull("a\\..\\b"))
        assertNull(WorkspacePath.parseOrNull("..\\secret"))
    }

    @Test
    fun rejectsControlCharactersIncludingNulAndNewlines() {
        listOf("a\u0000b", "a\nb", "a\rb", "a\tb", "a\u007Fb").forEach {
            assertNull("should reject control char path", WorkspacePath.parseOrNull(it))
        }
    }

    @Test
    fun rejectsEmptyAndBlank() {
        assertNull(WorkspacePath.parseOrNull(""))
        assertNull(WorkspacePath.parseOrNull("   "))
    }

    @Test
    fun enforcesLengthLimits() {
        val longSegment = "a".repeat(WorkspacePath.MAX_SEGMENT_LENGTH + 1)
        assertNull(WorkspacePath.parseOrNull(longSegment))
        assertNotNull(WorkspacePath.parseOrNull("a".repeat(WorkspacePath.MAX_SEGMENT_LENGTH)))
        val manySegments = List(400) { "abcd" }.joinToString("/")
        assertNull(WorkspacePath.parseOrNull(manySegments))
    }

    @Test
    fun parseThrowsWithReadableReason() {
        val error = assertThrows(IllegalArgumentException::class.java) { WorkspacePath.parse("a/../b") }
        assertEquals("Path must not contain '.' or '..' segments.", error.message)
    }

    @Test
    fun equalityIsByValue() {
        assertEquals(WorkspacePath.parse("a/b"), WorkspacePath.parse("a/b"))
    }
}
