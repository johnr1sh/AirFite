package dev.johnrish.pocketengineer

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
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
    }

    fun listFiles(): List<WorkspaceEntry> {
        val output = mutableListOf<WorkspaceEntry>()
        walk(DocumentFile.fromTreeUri(context, treeUri), "", 0, output)
        return output.sortedBy { it.relativePath.lowercase() }
    }

    private fun walk(folder: DocumentFile?, parent: String, depth: Int, output: MutableList<WorkspaceEntry>) {
        if (folder == null || depth > MAX_DEPTH || output.size >= MAX_FILES) return
        for (child in folder.listFiles().sortedBy { it.name?.lowercase().orEmpty() }) {
            if (output.size >= MAX_FILES) break
            val name = child.name ?: continue
            if (name.startsWith(".") || name in setOf("node_modules", "build", ".git", ".gradle")) continue
            val path = if (parent.isEmpty()) name else "$parent/$name"
            output.add(WorkspaceEntry(name, child.uri, child.isDirectory, child.length(), path))
            if (child.isDirectory) walk(child, path, depth + 1, output)
        }
    }

    fun readFile(uri: Uri): String {
        val entry = listFiles().firstOrNull { it.uri == uri && !it.isDirectory }
            ?: throw SecurityException("File is outside the selected workspace or no longer exists.")
        if (entry.size > MAX_TEXT_BYTES) throw IllegalArgumentException("File exceeds the 512 KB preview limit.")
        context.contentResolver.openInputStream(entry.uri)?.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                total += count
                if (total > MAX_TEXT_BYTES) throw IllegalArgumentException("File exceeds the 512 KB preview limit.")
                output.write(buffer, 0, count)
            }
            return output.toByteArray().toString(Charsets.UTF_8)
        }
        throw IllegalStateException("Unable to open file.")
    }

    fun search(query: String): List<String> {
        val needle = query.trim()
        if (needle.isEmpty()) return emptyList()
        return listFiles().asSequence().filter { !it.isDirectory && it.size <= MAX_TEXT_BYTES }
            .mapNotNull { entry ->
                runCatching { readFile(entry.uri) }.getOrNull()?.let { content ->
                    if (content.contains(needle, ignoreCase = true)) entry.relativePath else null
                }
            }.take(50).toList()
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
                .put("properties", JSONObject().put("query", JSONObject().put("type", "string")))
                .put("required", JSONArray().put("query")).put("additionalProperties", false))
    )

    /** Local validated dispatch; a future MCP transport can reuse this entry point. */
    fun callTool(name: String, arguments: JSONObject): JSONObject = when (name) {
        "workspace.list_files" -> JSONObject().put("files", JSONArray().apply {
            listFiles().forEach { put(JSONObject().put("path", it.relativePath).put("directory", it.isDirectory).put("size", it.size)) }
        })
        "workspace.read_file" -> {
            val path = arguments.optString("path").trim()
            require(path.isNotEmpty() && !path.startsWith("/") && !path.split('/').any { it == ".." }) { "Invalid relative path." }
            val entry = listFiles().firstOrNull { it.relativePath == path && !it.isDirectory }
                ?: throw SecurityException("Path is not within the selected workspace.")
            JSONObject().put("path", path).put("content", readFile(entry.uri))
        }
        "workspace.search_files" -> JSONObject().put("matches", JSONArray(search(arguments.optString("query"))))
        else -> throw IllegalArgumentException("Unknown tool: $name")
    }
}
