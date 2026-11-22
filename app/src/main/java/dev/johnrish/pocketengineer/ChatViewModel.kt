package dev.johnrish.pocketengineer

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

data class ChatMessage(val role: String, val content: String)

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("provider", 0)
    private val secureKeys = SecureKeyStore()
    private val client = OkHttpClient.Builder().callTimeout(90, TimeUnit.SECONDS).build()
    var messages by androidx.compose.runtime.mutableStateOf(listOf<ChatMessage>())
        private set
    var input by androidx.compose.runtime.mutableStateOf("")
    var busy by androidx.compose.runtime.mutableStateOf(false)
    var baseUrl by androidx.compose.runtime.mutableStateOf(prefs.getString("baseUrl", "https://api.openai.com/v1") ?: "")
    var apiKey by androidx.compose.runtime.mutableStateOf(loadApiKey(app))
    var model by androidx.compose.runtime.mutableStateOf(prefs.getString("model", "gpt-4o-mini") ?: "")
    var settingsError by androidx.compose.runtime.mutableStateOf<String?>(null)
        private set
    var workspaceUri by androidx.compose.runtime.mutableStateOf(prefs.getString("workspaceUri", null)?.let(Uri::parse))
        private set
    var workspaceName by androidx.compose.runtime.mutableStateOf<String?>(null)
        private set
    var workspaceFiles by androidx.compose.runtime.mutableStateOf<List<WorkspaceEntry>>(emptyList())
        private set
    var workspaceSearch by androidx.compose.runtime.mutableStateOf("")
    var selectedFileName by androidx.compose.runtime.mutableStateOf<String?>(null)
        private set
    var selectedFileContent by androidx.compose.runtime.mutableStateOf<String?>(null)
        private set
    var workspaceError by androidx.compose.runtime.mutableStateOf<String?>(null)
        private set

    private fun loadApiKey(app: Application): String {
        val stored = prefs.getString("encryptedApiKey", "").orEmpty()
        if (stored.isNotBlank()) return runCatching { secureKeys.decrypt(stored) }.getOrDefault("")
        val legacy = prefs.getString("apiKey", "").orEmpty()
        if (legacy.isNotBlank()) runCatching {
            prefs.edit().putString("encryptedApiKey", secureKeys.encrypt(legacy)).remove("apiKey").apply()
        }
        return legacy
    }

    init { refreshWorkspace() }

    fun saveSettings(): Boolean {
        return runCatching {
            val encrypted = secureKeys.encrypt(apiKey)
            prefs.edit().putString("baseUrl", baseUrl.trim().trimEnd('/'))
                .putString("encryptedApiKey", encrypted).remove("apiKey").putString("model", model).apply()
            settingsError = null
        }.onFailure { settingsError = it.message ?: "Unable to securely save settings." }.isSuccess
    }

    fun setWorkspace(uri: Uri) {
        workspaceUri = uri
        prefs.edit().putString("workspaceUri", uri.toString()).apply()
        selectedFileName = null
        selectedFileContent = null
        refreshWorkspace()
    }

    fun refreshWorkspace() {
        val uri = workspaceUri ?: return
        runCatching {
            val root = androidx.documentfile.provider.DocumentFile.fromTreeUri(getApplication(), uri)
                ?: throw IllegalStateException("Folder permission is unavailable. Select the folder again.")
            workspaceName = root.name ?: uri.lastPathSegment
            workspaceFiles = WorkspaceTools(getApplication<Application>(), uri).listFiles()
            workspaceError = null
        }.onFailure { workspaceError = it.message ?: "Unable to access workspace." }
    }

    fun openWorkspaceFile(entry: WorkspaceEntry) {
        if (entry.isDirectory) return
        runCatching {
            selectedFileContent = WorkspaceTools(getApplication<Application>(), workspaceUri ?: return)
                .readFile(entry.uri)
            selectedFileName = entry.relativePath
            workspaceError = null
        }.onFailure { workspaceError = it.message ?: "Unable to read file." }
    }

    fun workspaceToolManifest(): JSONArray = workspaceUri?.let {
        WorkspaceTools(getApplication<Application>(), it).toolManifest()
    } ?: JSONArray()

    fun callWorkspaceTool(name: String, arguments: JSONObject): JSONObject {
        val uri = workspaceUri ?: throw IllegalStateException("Select a workspace first.")
        return WorkspaceTools(getApplication<Application>(), uri).callTool(name, arguments)
    }

    fun send() {
        val prompt = input.trim()
        if (prompt.isEmpty() || busy) return
        input = ""
        messages = messages + ChatMessage("user", prompt)
        busy = true
        viewModelScope.launch {
            try {
                val reply = withContext(Dispatchers.IO) { requestReply(prompt) }
                messages = messages + ChatMessage("assistant", reply)
            } catch (e: Exception) {
                messages = messages + ChatMessage("assistant", "Request failed: ${e.message ?: "Unknown error"}")
            } finally { busy = false }
        }
    }

    private fun requestReply(prompt: String): String {
        if (apiKey.isBlank()) return "Add your API key in Settings first."
        val endpoint = baseUrl.trim().trimEnd('/') + "/chat/completions"
        val body = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().put(JSONObject().put("role", "system").put("content", "You are AirFite, a concise mobile coding assistant. Explain plans before complex work."))
                .put(JSONObject().put("role", "user").put("content", prompt)))
        }.toString()
        val request = Request.Builder().url(endpoint).header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json").post(body.toRequestBody("application/json".toMediaType())).build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) return "HTTP ${response.code}: ${raw.take(1200)}"
            val json = JSONObject(raw)
            return json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
                ?.optString("content")?.takeIf { it.isNotBlank() } ?: "The provider returned an empty response."
        }
    }
}
