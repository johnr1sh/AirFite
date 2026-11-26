package dev.johnrish.pocketengineer.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceToolServiceTest {
    private class FakeDataSource(
        val entries: List<WorkspaceToolEntry> = listOf(
            WorkspaceToolEntry("README.md", false, 12),
            WorkspaceToolEntry("src", true, 0),
            WorkspaceToolEntry("src/Main.kt", false, 64),
            WorkspaceToolEntry("src/Main.kt.bak", false, 70)
        )
    ) : WorkspaceToolDataSource {
        var readPath: String? = null
        var searchQuery: String? = null
        var listCalls = 0

        override fun listEntries(): List<WorkspaceToolEntry> {
            listCalls++
            return entries
        }

        override fun readTextFile(relativePath: String): String {
            readPath = relativePath
            return "contents of $relativePath"
        }

        override fun searchFiles(query: String): List<String> {
            searchQuery = query
            return listOf("src/Main.kt")
        }
    }

    @Test
    fun listsImmutableWorkspaceMetadataThroughThePort() {
        val source = FakeDataSource()
        val result = WorkspaceToolService(source).execute(
            WorkspaceToolService.LIST_FILES,
            emptyMap()
        )

        assertEquals(
            WorkspaceToolResult.FileList(source.entries),
            result
        )
        assertEquals(1, source.listCalls)
    }

    @Test
    fun readsOnlyAnExactListedFilePath() {
        val source = FakeDataSource()
        val result = WorkspaceToolService(source).execute(
            WorkspaceToolService.READ_FILE,
            mapOf("path" to "src/Main.kt")
        )

        assertEquals(WorkspaceToolResult.FileContent("src/Main.kt", "contents of src/Main.kt"), result)
        assertEquals("src/Main.kt", source.readPath)
    }

    @Test
    fun rejectsTraversalBeforeConsultingTheDataSource() {
        val source = FakeDataSource()

        assertThrows(IllegalArgumentException::class.java) {
            WorkspaceToolService(source).execute(
                WorkspaceToolService.READ_FILE,
                mapOf("path" to "../outside.txt")
            )
        }

        assertEquals(0, source.listCalls)
        assertEquals(null, source.readPath)
    }

    @Test
    fun rejectsMissingPathsDirectoriesAndAmbiguousEntries() {
        val source = FakeDataSource()

        assertThrows(SecurityException::class.java) {
            WorkspaceToolService(source).execute(
                WorkspaceToolService.READ_FILE,
                mapOf("path" to "src/Missing.kt")
            )
        }
        assertThrows(SecurityException::class.java) {
            WorkspaceToolService(source).execute(
                WorkspaceToolService.READ_FILE,
                mapOf("path" to "src")
            )
        }

        val duplicateSource = FakeDataSource(
            entries = listOf(
                WorkspaceToolEntry("src/Main.kt", false, 1),
                WorkspaceToolEntry("src/Main.kt", false, 2)
            )
        )
        assertThrows(SecurityException::class.java) {
            WorkspaceToolService(duplicateSource).execute(
                WorkspaceToolService.READ_FILE,
                mapOf("path" to "src/Main.kt")
            )
        }
        assertEquals(null, source.readPath)
        assertEquals(null, duplicateSource.readPath)
    }

    @Test
    fun rejectsUnknownToolsAndUnexpectedArguments() {
        val source = FakeDataSource()
        val service = WorkspaceToolService(source)

        assertThrows(IllegalArgumentException::class.java) {
            service.execute("workspace.delete_file", emptyMap())
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.execute(WorkspaceToolService.LIST_FILES, mapOf("path" to "README.md"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.execute(WorkspaceToolService.READ_FILE, mapOf("path" to "README.md", "extra" to true))
        }
    }

    @Test
    fun rejectsMissingAndNonStringArguments() {
        val service = WorkspaceToolService(FakeDataSource())

        assertThrows(IllegalArgumentException::class.java) {
            service.execute(WorkspaceToolService.READ_FILE, emptyMap())
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.execute(WorkspaceToolService.READ_FILE, mapOf("path" to 42))
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.execute(WorkspaceToolService.SEARCH_FILES, mapOf("query" to false))
        }
    }

    @Test
    fun trimsSearchInputAndEnforcesTheCharacterLimit() {
        val source = FakeDataSource()
        val service = WorkspaceToolService(source, maxSearchQueryChars = 5)

        val result = service.execute(
            WorkspaceToolService.SEARCH_FILES,
            mapOf("query" to "  main  ")
        )
        assertEquals(WorkspaceToolResult.SearchMatches(listOf("src/Main.kt")), result)
        assertEquals("main", source.searchQuery)

        assertThrows(IllegalArgumentException::class.java) {
            service.execute(
                WorkspaceToolService.SEARCH_FILES,
                mapOf("query" to "123456")
            )
        }
        assertEquals("main", source.searchQuery)
    }

    @Test
    fun blankSearchDoesNotTouchTheDataSource() {
        val source = FakeDataSource()
        val result = WorkspaceToolService(source).execute(
            WorkspaceToolService.SEARCH_FILES,
            mapOf("query" to "   ")
        )

        assertEquals(WorkspaceToolResult.SearchMatches(emptyList()), result)
        assertEquals(null, source.searchQuery)
        assertTrue(source.listCalls == 0)
    }
}
