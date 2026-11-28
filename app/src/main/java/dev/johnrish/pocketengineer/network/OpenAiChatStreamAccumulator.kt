package dev.johnrish.pocketengineer.network

import org.json.JSONArray
import org.json.JSONObject
import java.util.TreeMap

/**
 * Reassembles Chat Completions SSE deltas into the message shape used by the existing agent loop.
 * Provider-controlled tool arguments remain fragments until the complete message is validated by
 * AgentToolCallValidator; this class never dispatches tools.
 */
class OpenAiChatStreamAccumulator(
    private val maxContentChars: Int = MAX_CONTENT_CHARS,
    private val maxToolCalls: Int = MAX_TOOL_CALLS,
    private val maxToolArgumentChars: Int = MAX_TOOL_ARGUMENT_CHARS
) {
    private data class ToolCall(
        var id: String = "",
        var type: String = "function",
        val name: StringBuilder = StringBuilder(),
        val arguments: StringBuilder = StringBuilder()
    )

    private val content = StringBuilder()
    private val toolCalls = TreeMap<Int, ToolCall>()
    var isDone: Boolean = false
        private set

    val contentText: String
        get() = content.toString()

    val hasToolCalls: Boolean
        get() = toolCalls.isNotEmpty()

    init {
        require(maxContentChars > 0) { "Content limit must be positive." }
        require(maxToolCalls > 0) { "Tool-call limit must be positive." }
        require(maxToolArgumentChars > 0) { "Tool argument limit must be positive." }
    }

    /**
     * Accepts one complete SSE event. Returns the latest accumulated visible text when it changes,
     * or null for control/metadata/tool-only events.
     */
    fun accept(event: ServerSentEvent): String? {
        if (isDone) return null
        val payload = event.data
        if (payload == "[DONE]") {
            isDone = true
            return null
        }
        if (payload.isBlank()) return null

        val root = try {
            JSONObject(payload)
        } catch (_: Exception) {
            throw IllegalArgumentException("Provider returned a malformed streaming event.")
        }
        if (event.type == "error" && !root.has("error")) {
            throw IllegalStateException("Provider returned a streaming error event.")
        }
        root.optJSONObject("error")?.let { error ->
            val message = error.optString("message").takeIf { it.isNotBlank() }
            throw IllegalStateException(message?.take(400) ?: "Provider returned a streaming error.")
        }

        val choicesValue = root.opt("choices")
        if (choicesValue == null || choicesValue == JSONObject.NULL) return null
        if (choicesValue !is JSONArray) {
            throw IllegalArgumentException("Provider returned an invalid streamed choices value.")
        }
        val choices = choicesValue
        if (choices.length() == 0) return null
        val choice = choices.optJSONObject(0)
            ?: throw IllegalArgumentException("Provider returned an invalid streamed choice.")
        val delta = choice.optJSONObject("delta") ?: return null

        var contentChanged = false
        val text = delta.opt("content")
        if (text != null && text != JSONObject.NULL && text !is String) {
            throw IllegalArgumentException("Provider returned an unsupported streamed content value.")
        }
        if (text is String && text.isNotEmpty()) {
            if (text.length > maxContentChars - content.length) {
                throw IllegalArgumentException("Provider response exceeds the configured text limit.")
            }
            content.append(text)
            contentChanged = true
        }

        val callValue = delta.opt("tool_calls")
        if (callValue != null && callValue != JSONObject.NULL && callValue !is JSONArray) {
            throw IllegalArgumentException("Provider returned an invalid streamed tool-call list.")
        }
        val callDeltas = callValue as? JSONArray
        if (callDeltas != null) {
            for (index in 0 until callDeltas.length()) {
                val fragment = callDeltas.optJSONObject(index)
                    ?: throw IllegalArgumentException("Provider returned an invalid streamed tool call.")
                val callIndex = fragment.optInt("index", -1)
                if (callIndex !in 0 until maxToolCalls) {
                    throw IllegalArgumentException("Streamed tool call index is outside the configured limit.")
                }
                val call = toolCalls[callIndex] ?: run {
                    if (toolCalls.size >= maxToolCalls) {
                        throw IllegalArgumentException("Provider exceeded the tool-call limit.")
                    }
                    ToolCall().also { toolCalls[callIndex] = it }
                }

                fragment.optString("id").takeIf { it.isNotBlank() }?.let { id ->
                    if (id.length > MAX_TOOL_ID_CHARS) {
                        throw IllegalArgumentException("Provider tool-call ID exceeds the configured limit.")
                    }
                    if (call.id.isNotBlank() && call.id != id) {
                        throw IllegalArgumentException("Provider changed a streamed tool-call ID.")
                    }
                    call.id = id
                }
                fragment.optString("type").takeIf { it.isNotBlank() }?.let { type ->
                    if (type != "function") {
                        throw IllegalArgumentException("Provider returned an unsupported streamed tool-call type.")
                    }
                    call.type = type
                }

                val functionValue = fragment.opt("function")
                if (functionValue != null && functionValue != JSONObject.NULL && functionValue !is JSONObject) {
                    throw IllegalArgumentException("Provider returned an invalid streamed tool-call function.")
                }
                val function = functionValue as? JSONObject
                if (function != null) {
                    appendBounded(call.name, function.optString("name"), MAX_TOOL_NAME_CHARS, "tool name")
                    appendBounded(
                        call.arguments,
                        function.optString("arguments"),
                        maxToolArgumentChars,
                        "tool arguments"
                    )
                }
            }
        }

        return if (contentChanged) content.toString() else null
    }

    /** Builds a normal assistant message for the existing schema validator and tool loop. */
    fun toAssistantMessage(): JSONObject {
        val message = JSONObject()
            .put("role", "assistant")
            .put("content", content.toString())

        if (toolCalls.isNotEmpty()) {
            val calls = JSONArray()
            toolCalls.forEach { (_, call) ->
                calls.put(
                    JSONObject()
                        .put("id", call.id)
                        .put("type", call.type)
                        .put(
                            "function",
                            JSONObject()
                                .put("name", call.name.toString())
                                .put("arguments", call.arguments.toString())
                        )
                )
            }
            message.put("tool_calls", calls)
        }
        return message
    }

    private fun appendBounded(target: StringBuilder, value: String, limit: Int, label: String) {
        if (value.length > limit - target.length) {
            throw IllegalArgumentException("Provider $label exceed the configured limit.")
        }
        target.append(value)
    }

    companion object {
        const val MAX_CONTENT_CHARS = 2 * 1024 * 1024
        const val MAX_TOOL_CALLS = 8
        const val MAX_TOOL_ARGUMENT_CHARS = 32 * 1024
        const val MAX_TOOL_ID_CHARS = 128
        const val MAX_TOOL_NAME_CHARS = 128
    }
}
