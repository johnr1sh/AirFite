package dev.johnrish.pocketengineer

import android.app.Application
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
import java.util.concurrent.TimeUnit

data class ChatMessage(val role: String, val content: String)

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("provider", 0)
    private val client = OkHttpClient.Builder().callTimeout(90, TimeUnit.SECONDS).build()
    var messages by androidx.compose.runtime.mutableStateOf(listOf<ChatMessage>())
        private set
    var input by androidx.compose.runtime.mutableStateOf("")
    var busy by androidx.compose.runtime.mutableStateOf(false)
    var baseUrl by androidx.compose.runtime.mutableStateOf(prefs.getString("baseUrl", "https://api.openai.com/v1") ?: "")
    var apiKey by androidx.compose.runtime.mutableStateOf(prefs.getString("apiKey", "") ?: "")
    var model by androidx.compose.runtime.mutableStateOf(prefs.getString("model", "gpt-4o-mini") ?: "")

    fun saveSettings() {
        prefs.edit().putString("baseUrl", baseUrl.trim().trimEnd('/')).putString("apiKey", apiKey).putString("model", model).apply()
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
            put("messages", JSONArray().put(JSONObject().put("role", "system").put("content", "You are Pocket Engineer, a concise mobile coding assistant. Explain your plan before complex code changes."))
                .put(JSONObject().put("role", "user").put("content", prompt)))
        }.toString()
        val request = Request.Builder().url(endpoint).header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json").post(body.toRequestBody("application/json".toMediaType())).build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) return "HTTP ${response.code}: ${raw.take(1200)}"
            return JSONObject(raw).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")?.takeIf { it.isNotBlank() } ?: "The provider returned an empty response."
        }
    }
}
