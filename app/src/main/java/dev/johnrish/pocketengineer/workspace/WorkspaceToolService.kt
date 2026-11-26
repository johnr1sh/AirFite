package dev.johnrish.pocketengineer.workspace

/**
 * Android-free metadata exposed to the workspace agent.
 *
 * This deliberately contains no Uri, DocumentFile, Context, or provider-specific handle. The
 * Android adapter remains responsible for resolving a path against the currently granted SAF tree.
 */
data class WorkspaceToolEntry(
    val relativePath: String,
    val isDirectory: Boolean,
    val size: Long
)

/** Boundary between tool policy and the Android Storage Access Framework implementation. */
interface WorkspaceToolDataSource {
    fun listEntries(): List<WorkspaceToolEntry>

    /** Must re-resolve [relativePath] against the live, user-authorized workspace before reading. */
    fun readTextFile(relativePath: String): String

    fun searchFiles(query: String): List<String>
}

sealed interface WorkspaceToolResult {
    data class FileList(val entries: List<WorkspaceToolEntry>) : WorkspaceToolResult
    data class FileContent(val path: String, val content: String) : WorkspaceToolResult
    data class SearchMatches(val paths: List<String>) : WorkspaceToolResult
}

/**
 * Validates and dispatches read-only workspace tool requests without depending on Android or JSON.
 *
 * The service checks path syntax and exact membership before calling the data source. The source
 * must repeat live authorization at the point of I/O to avoid treating an earlier listing as a
 * durable permission grant.
 */
class WorkspaceToolService(
    private val dataSource: WorkspaceToolDataSource,
    private val maxSearchQueryChars: Int = DEFAULT_MAX_SEARCH_QUERY_CHARS
) {
    init {
        require(maxSearchQueryChars > 0) { "Search query limit must be positive." }
    }

    fun execute(name: String, arguments: Map<String, Any?>): WorkspaceToolResult {
        return when (name) {
            LIST_FILES -> {
                requireOnlyKeys(arguments, emptySet())
                WorkspaceToolResult.FileList(dataSource.listEntries().toList())
            }

            READ_FILE -> {
                requireOnlyKeys(arguments, setOf(PATH_ARGUMENT))
                val rawPath = arguments[PATH_ARGUMENT] as? String
                    ?: throw IllegalArgumentException("Tool path must be a string.")
                val path = WorkspacePath.parse(rawPath)
                val matches = dataSource.listEntries().filter { it.relativePath == path.value }
                if (matches.size != 1 || matches.single().isDirectory) {
                    throw SecurityException("Path is not a unique file inside the selected workspace.")
                }
                WorkspaceToolResult.FileContent(path.value, dataSource.readTextFile(path.value))
            }

            SEARCH_FILES -> {
                requireOnlyKeys(arguments, setOf(QUERY_ARGUMENT))
                val query = arguments[QUERY_ARGUMENT] as? String
                    ?: throw IllegalArgumentException("Search query must be a string.")
                val needle = query.trim()
                if (needle.length > maxSearchQueryChars) {
                    throw IllegalArgumentException("Search query exceeds the $maxSearchQueryChars character limit.")
                }
                WorkspaceToolResult.SearchMatches(
                    if (needle.isEmpty()) emptyList() else dataSource.searchFiles(needle).toList()
                )
            }

            else -> throw IllegalArgumentException("Unknown workspace tool.")
        }
    }

    private fun requireOnlyKeys(arguments: Map<String, Any?>, allowed: Set<String>) {
        if (arguments.keys.any { it !in allowed }) {
            throw IllegalArgumentException("Tool arguments contain an unsupported field.")
        }
    }

    companion object {
        const val LIST_FILES = "workspace.list_files"
        const val READ_FILE = "workspace.read_file"
        const val SEARCH_FILES = "workspace.search_files"
        const val PATH_ARGUMENT = "path"
        const val QUERY_ARGUMENT = "query"
        const val DEFAULT_MAX_SEARCH_QUERY_CHARS = 256
    }
}
