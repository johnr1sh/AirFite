package dev.johnrish.pocketengineer.workspace

import java.security.MessageDigest

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
    data class PatchProposal(
        val path: String,
        val rationale: String,
        val lineNumber: Int,
        val before: String,
        val after: String,
        val diffPreview: String,
        val baseSha256: String,
        val proposedSha256: String
    ) : WorkspaceToolResult
}

/**
 * Validates and dispatches workspace tool requests without depending on Android or JSON.
 *
 * Read access is limited to exact entries in the current user-granted workspace. Patch proposals
 * are read-only: one bounded, unique, whole-line anchor is replaced in memory and returned as a
 * preview. No data-source write method exists in this interface.
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
                val path = parseSafePath(stringArgument(arguments, PATH_ARGUMENT))
                val matches = dataSource.listEntries().filter { it.relativePath == path.value }
                if (matches.size != 1 || matches.single().isDirectory) {
                    throw SecurityException("Path is not a unique file inside the selected workspace.")
                }
                WorkspaceToolResult.FileContent(path.value, dataSource.readTextFile(path.value))
            }

            SEARCH_FILES -> {
                requireOnlyKeys(arguments, setOf(QUERY_ARGUMENT))
                val needle = stringArgument(arguments, QUERY_ARGUMENT).trim()
                if (needle.length > maxSearchQueryChars) {
                    throw IllegalArgumentException("Search query exceeds the $maxSearchQueryChars character limit.")
                }
                WorkspaceToolResult.SearchMatches(
                    if (needle.isEmpty()) emptyList() else dataSource.searchFiles(needle).toList()
                )
            }

            PROPOSE_PATCH -> proposePatch(arguments)

            else -> throw IllegalArgumentException("Unknown workspace tool.")
        }
    }

    private fun proposePatch(arguments: Map<String, Any?>): WorkspaceToolResult.PatchProposal {
        requireOnlyKeys(
            arguments,
            setOf(PATH_ARGUMENT, EXPECTED_TEXT_ARGUMENT, REPLACEMENT_TEXT_ARGUMENT, RATIONALE_ARGUMENT)
        )
        val path = parseSafePath(stringArgument(arguments, PATH_ARGUMENT))
        val expected = stringArgument(arguments, EXPECTED_TEXT_ARGUMENT)
        val replacement = stringArgument(arguments, REPLACEMENT_TEXT_ARGUMENT)
        val rationale = stringArgument(arguments, RATIONALE_ARGUMENT).trim()

        if (expected.isEmpty()) throw IllegalArgumentException("Patch anchor must not be empty.")
        if (expected.length > MAX_PATCH_SNIPPET_CHARS || replacement.length > MAX_PATCH_SNIPPET_CHARS) {
            throw IllegalArgumentException("Patch snippets must not exceed $MAX_PATCH_SNIPPET_CHARS characters each.")
        }
        if (rationale.isEmpty() || rationale.length > MAX_PATCH_RATIONALE_CHARS) {
            throw IllegalArgumentException("Patch rationale must contain 1 to $MAX_PATCH_RATIONALE_CHARS characters.")
        }
        if (expected.any { it.code == 0 } || replacement.any { it.code == 0 }) {
            throw IllegalArgumentException("Patch snippets must not contain NUL characters.")
        }
        if (expected == replacement) throw IllegalArgumentException("Patch does not change the selected text.")

        val matches = dataSource.listEntries().filter { it.relativePath == path.value }
        if (matches.size != 1 || matches.single().isDirectory) {
            throw SecurityException("Path is not a unique file inside the selected workspace.")
        }

        val current = dataSource.readTextFile(path.value)
        if (current.length > MAX_PATCH_SOURCE_CHARS) {
            throw IllegalArgumentException("Source file exceeds the patch preview limit.")
        }
        val start = current.indexOf(expected)
        if (start < 0) {
            throw IllegalStateException("Patch is stale: the expected source text was not found. Read the file again.")
        }
        if (current.indexOf(expected, start + expected.length) >= 0) {
            throw IllegalStateException("Patch is ambiguous: the expected source text occurs more than once.")
        }

        val end = start + expected.length
        val atLineEnd = end == current.length || current[end] == '\n' ||
            (current[end] == '\r' && current.getOrNull(end + 1) == '\n')
        if ((start > 0 && current[start - 1] != '\n') || !atLineEnd) {
            throw IllegalArgumentException("Patch anchor must cover complete lines. Include the full line text.")
        }
        if (end < current.length && expected.endsWith("\n") != replacement.endsWith("\n")) {
            throw IllegalArgumentException("Patch must preserve the line ending before the following source line.")
        }

        val proposed = current.replaceRange(start, end, replacement)
        if (proposed.length > MAX_PATCH_SOURCE_CHARS) {
            throw IllegalArgumentException("Proposed source file exceeds the patch preview limit.")
        }
        SensitiveFileGuard.requireSafeContent(proposed)

        val lineNumber = current.substring(0, start).count { it == '\n' } + 1
        val diffPreview = buildString {
            append("--- a/").append(path.value).append('\n')
            append("+++ b/").append(path.value).append('\n')
            append("@@ line ").append(lineNumber).append(" @@").append('\n')
            expected.removeSuffix("\n").split('\n').forEach { append("- ").append(it.removeSuffix("\r")).append('\n') }
            if (replacement.isNotEmpty()) {
                replacement.removeSuffix("\n").split('\n').forEach { append("+ ").append(it.removeSuffix("\r")).append('\n') }
            }
        }
        return WorkspaceToolResult.PatchProposal(
            path = path.value,
            rationale = rationale,
            lineNumber = lineNumber,
            before = expected,
            after = replacement,
            diffPreview = diffPreview,
            baseSha256 = sha256(current),
            proposedSha256 = sha256(proposed)
        )
    }

    private fun parseSafePath(raw: String): WorkspacePath {
        val path = WorkspacePath.parse(raw)
        SensitiveFileGuard.requireSafePath(path.value)
        return path
    }

    private fun stringArgument(arguments: Map<String, Any?>, key: String): String =
        arguments[key] as? String ?: throw IllegalArgumentException("Tool field '$key' must be a string.")

    private fun requireOnlyKeys(arguments: Map<String, Any?>, allowed: Set<String>) {
        if (arguments.keys.any { it !in allowed }) {
            throw IllegalArgumentException("Tool arguments contain an unsupported field.")
        }
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    companion object {
        const val LIST_FILES = "workspace.list_files"
        const val READ_FILE = "workspace.read_file"
        const val SEARCH_FILES = "workspace.search_files"
        const val PROPOSE_PATCH = "workspace.propose_patch"
        const val PATH_ARGUMENT = "path"
        const val QUERY_ARGUMENT = "query"
        const val EXPECTED_TEXT_ARGUMENT = "expectedText"
        const val REPLACEMENT_TEXT_ARGUMENT = "replacementText"
        const val RATIONALE_ARGUMENT = "rationale"
        const val DEFAULT_MAX_SEARCH_QUERY_CHARS = 256
        const val MAX_PATCH_SNIPPET_CHARS = 4 * 1024
        const val MAX_PATCH_RATIONALE_CHARS = 400
        const val MAX_PATCH_SOURCE_CHARS = 512 * 1024
    }
}
