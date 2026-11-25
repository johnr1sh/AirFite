package dev.johnrish.pocketengineer

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dev.johnrish.pocketengineer.workspace.WorkspacePath
import dev.johnrish.pocketengineer.workspace.WorkspaceIgnoreRules
import dev.johnrish.pocketengineer.workspace.SensitiveFileGuard
import dev.johnrish.pocketengineer.workspace.WorkspaceSearchBudget
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/** SAF-scoped workspace tools. Transport-neutral so it can later be exposed over MCP. */
data class WorkspaceEntry(val name: String, val uri: Uri, val isDirectory: Boolean, val size: Long, val relativePath: String)

class WorkspaceTools(private val context: Context, private val treeUri: Uri) {
    companion object {
        const val MAX_FILES = 500
        const val MAX_DEPTH = 8
        const val MAX_TEXT_BYTES = 512 * 1024
        const val MAX_SEARCH_FILES = 100
        const val MAX_SEARCH_FILE_BYTES = 32 * 1024
        const val MAX_SEARCH_TOTAL_BYTES = 2 * 1024 * 1024
        const val MAX_SEARCH_QUERY_CHARS = 256
        const val MAX_IGNORE_FILE_BYTES = 32 * 1024
    }

    fun listFiles(): List<WorkspaceEntry> {
        val root = DocumentFile.fromTreeUri(context, treeUri)
        val ignoreRules = loadRootIgnoreRules(root)
        val output = mutableListOf<WorkspaceEntry>()
        walk(root, "", 0, output, ignoreRules)
        return output.sortedBy { it.relativePath.lowercase() }
    }

    private fun walk(
        folder: DocumentFile?,
        parent: String,
        depth: Int,
        output: MutableList<WorkspaceEntry>,
        ignoreRules: WorkspaceIgnoreRules
    ) {
        if (folder == null || depth > MAX_DEPTH || output.size >= MAX_FILES) return
        for (child in folder.listFiles().sortedBy { it.name?.lowercase().orEmpty() }) {
            if (output.size >= MAX_FILES) break
            val name = child.name ?: continue
            val safeDotEnvTemplate = SensitiveFileGuard.isSafeDotEnvTemplateName(name)
            if ((name.startsWith(".") && !safeDotEnvTemplate) || name in setOf("node_modules", "build", ".git", ".gradle")) continue
            val path = if (parent.isEmpty()) name else "$parent/$name"
            if (ignoreRules.isIgnored(path, child.isDirectory)) continue
            output.add(WorkspaceEntry(name, child.uri, child.isDirectory, child.length(), path))
            if (child.isDirectory) walk(child, path, depth + 1, output, ignoreRules)
        }
    }

    private fun loadRootIgnoreRules(root: DocumentFile?): WorkspaceIgnoreRules {
        val ignoreFile = runCatching {
            root?.listFiles()?.firstOrNull { !it.isDirectory && it.name == ".gitignore" }
        }.getOrNull() ?: return WorkspaceIgnoreRules.EMPTY

        if (ignoreFile.length() > MAX_IGNORE_FILE_BYTES) return WorkspaceIgnoreRules.EMPTY
        return runCatching {
            context.contentResolver.openInputStream(ignoreFile.uri)?.use { stream ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                var total = 0
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > MAX_IGNORE_FILE_BYTES) return WorkspaceIgnoreRules.EMPTY
                    output.write(buffer, 0, count)
                }
                val bytes = output.toByteArray()
                if (bytes.any { it == 0.toByte() }) return WorkspaceIgnoreRules.EMPTY
                WorkspaceIgnoreRules.parse(bytes.toString(Charsets.UTF_8))
            } ?: WorkspaceIgnoreRules.EMPTY
        }.getOrDefault(WorkspaceIgnoreRules.EMPTY)
    }

    fun readFile(uri: Uri): String {
        val entry = listFiles().firstOrNull { it.uri == uri && !it.isDirectory }
            ?: throw SecurityException("File is outside the selected workspace or no longer exists.")
        return readAgentReadableEntry(entry)
    }

    /** Applies credential safeguards to both UI previews and AI tool reads. */
    private fun readAgentReadableEntry(entry: WorkspaceEntry): String {
        SensitiveFileGuard.requireSafePath(entry.relativePath)
        val content = readEntry(entry)
        SensitiveFileGuard.requireSafeContent(content)
        return content
    }

    private data class TextReadResult(val content: String, val bytesRead: Int)

    private fun readEntry(entry: WorkspaceEntry, maxBytes: Int = MAX_TEXT_BYTES): String =
        readTextEntry(entry, maxBytes, allowTruncated = false).content

    /** Search may inspect a bounded prefix when the remaining aggregate budget is smaller. */
    private fun readSearchEntry(entry: WorkspaceEntry, maxBytes: Int): TextReadResult =
        readTextEntry(entry, maxBytes, allowTruncated = true)

    private fun readTextEntry(
        entry: WorkspaceEntry,
        maxBytes: Int,
        allowTruncated: Boolean
    ): TextReadResult {
        require(maxBytes > 0) { "Read limit must be positive." }
        if (!allowTruncated && entry.size > maxBytes) {
            throw IllegalArgumentException("File exceeds the ${maxBytes / 1024} KB read limit.")
        }
        context.contentResolver.openInputStream(entry.uri)?.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (total < maxBytes) {
                val count = stream.read(buffer, 0, minOf(buffer.size, maxBytes - total))
                if (count < 0) break
                total += count
                output.write(buffer, 0, count)
            }
            if (!allowTruncated && total == maxBytes && stream.read() >= 0) {
                throw IllegalArgumentException("File exceeds the ${maxBytes / 1024} KB read limit.")
            }
            val bytes = output.toByteArray()
            if (bytes.any { it == 0.toByte() }) throw IllegalArgumentException("Binary files cannot be previewed as text.")
            return TextReadResult(bytes.toString(Charsets.UTF_8), bytes.size)
        }
        throw IllegalStateException("Unable to open file.")
    }

    fun search(query: String): List<String> {
        val needle = query.trim()
        if (needle.length > MAX_SEARCH_QUERY_CHARS) {
            throw IllegalArgumentException("Search query exceeds the $MAX_SEARCH_QUERY_CHARS character limit.")
        }
        if (needle.isEmpty()) return emptyList()
        val budget = WorkspaceSearchBudget(
            maxFiles = MAX_SEARCH_FILES,
            maxTotalBytes = MAX_SEARCH_TOTAL_BYTES,
            maxFileBytes = MAX_SEARCH_FILE_BYTES
        )
        val matches = mutableListOf<String>()
        for (entry in listFiles()) {
            if (entry.isDirectory || SensitiveFileGuard.isSensitivePath(entry.relativePath) || entry.size > MAX_SEARCH_FILE_BYTES) continue
            val readLimit = budget.beginFile() ?: break
            val readResult = try {
                readSearchEntry(entry, readLimit)
            } catch (_: Exception) {
                // A failed or binary read may already have consumed bytes. Charge its full
                // reservation so repeated failures cannot bypass the aggregate I/O budget.
                budget.abandonFile()
                continue
            }
            budget.finishFile(readResult.bytesRead)
            if (readResult.content.contains(needle, ignoreCase = true)) {
                matches.add(entry.relativePath)
                if (matches.size >= 50) break
            }
        }
        return matches
    }

    /** MCP-style tool descriptors; mutating tools are intentionally withheld. */
    fun toolManifest(): JSONArray = JSONArray().put(
        JSONObject().put("name", "workspace.list_files")
            .put("description", "List files in the user-authorized workspace.")
            .put("inputSchema", JSONObject().put("type", "object").put("properties", JSONObject()).put("additionalProperties", false))
    ).put(
        JSONObject().put("name", "workspace.read_file")
            .put("description", "Read a text file inside the authorized workspace by relative path.")
            .put("inputSchema", JSONObject().put("type", "object")
                .put("properties", JSONObject().put("path", JSONObject().put("type", "string")))
                .put("required", JSONArray().put("path")).put("additionalProperties", false))
    ).put(
        JSONObject().put("name", "workspace.search_files")
            .put("description", "Search text in eligible workspace files.")
            .put("inputSchema", JSONObject().put("type", "object")
                .put("properties", JSONObject().put("query", JSONObject().put("type", "string")
                    .put("minLength", 1).put("maxLength", MAX_SEARCH_QUERY_CHARS)))
                .put("required", JSONArray().put("query")).put("additionalProperties", false))
    )

    /** Local validated dispatch; a future MCP transport can reuse this entry point. */
    fun callTool(name: String, arguments: JSONObject): JSONObject = when (name) {
        "workspace.list_files" -> JSONObject().put("files", JSONArray().apply {
            listFiles().forEach { put(JSONObject().put("path", it.relativePath).put("directory", it.isDirectory).put("size", it.size)) }
        })
        "workspace.read_file" -> {
            val path = WorkspacePath.parse(arguments.optString("path")).value
            // Exact match against the SAF listing is the actual access check; parse() only rejects unsafe syntax.
            val entry = listFiles().firstOrNull { it.relativePath == path && !it.isDirectory }
                ?: throw SecurityException("Path is not within the selected workspace.")
            JSONObject().put("path", path).put("content", readAgentReadableEntry(entry))
        }
        "workspace.search_files" -> JSONObject().put("matches", JSONArray(search(arguments.optString("query"))))
        else -> throw IllegalArgumentException("Unknown tool: $name")
    }
}
