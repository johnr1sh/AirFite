package dev.johnrish.pocketengineer.agent

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** A provider tool call after basic structural validation. */
data class ValidatedAgentToolCall(
    val id: String,
    val name: String,
    val arguments: JSONObject
)

sealed interface AgentToolCallValidation {
    data class Valid(val calls: List<ValidatedAgentToolCall>) : AgentToolCallValidation
    data class Invalid(val reason: String) : AgentToolCallValidation
}

/**
 * Validate provider-controlled tool-call envelopes before dispatch.
 *
 * This protects the local dispatcher from malformed IDs, unknown tools, non-object arguments,
 * duplicate IDs, and oversized argument payloads. It is not a substitute for each tool's own
 * argument validation or authorization checks.
 */
object AgentToolCallValidator {
    const val MAX_ID_LENGTH = 128
    const val MAX_TOOL_NAME_LENGTH = 128
    const val MAX_ARGUMENTS_CHARS = 16 * 1024
    const val MIN_RESULT_LIMIT_CHARS = 256

    fun validate(
        calls: JSONArray,
        registeredToolNames: Set<String>,
        maxCalls: Int
    ): AgentToolCallValidation {
        if (maxCalls <= 0) return AgentToolCallValidation.Invalid("Tool-call limit is unavailable.")
        if (calls.length() == 0) return AgentToolCallValidation.Invalid("Provider returned an empty tool-call list.")
        if (calls.length() > maxCalls) {
            return AgentToolCallValidation.Invalid("Provider requested too many tools in one round.")
        }

        val ids = HashSet<String>(calls.length())
        val validated = ArrayList<ValidatedAgentToolCall>(calls.length())
        for (index in 0 until calls.length()) {
            val call = calls.optJSONObject(index)
                ?: return AgentToolCallValidation.Invalid("Provider returned a malformed tool call.")
            val id = (call.opt("id") as? String)?.trim().orEmpty()
            if (id.isEmpty() || id.length > MAX_ID_LENGTH || id.any(Char::isISOControl)) {
                return AgentToolCallValidation.Invalid("Provider returned a tool call with an invalid ID.")
            }
            if (!ids.add(id)) {
                return AgentToolCallValidation.Invalid("Provider returned duplicate tool-call IDs.")
            }

            val function = call.optJSONObject("function")
                ?: return AgentToolCallValidation.Invalid("Provider returned a tool call without a function.")
            val name = (function.opt("name") as? String)?.trim().orEmpty()
            if (name.isEmpty() || name.length > MAX_TOOL_NAME_LENGTH || name.any(Char::isISOControl)) {
                return AgentToolCallValidation.Invalid("Provider returned a tool call with an invalid name.")
            }
            if (name !in registeredToolNames) {
                return AgentToolCallValidation.Invalid("Provider requested an unavailable workspace tool.")
            }

            val rawArguments = function.opt("arguments")
            val argumentsText = when (rawArguments) {
                null -> "{}"
                is String -> rawArguments.ifBlank { "{}" }
                is JSONObject -> rawArguments.toString()
                else -> return AgentToolCallValidation.Invalid("Tool arguments must be a JSON object.")
            }
            if (argumentsText.length > MAX_ARGUMENTS_CHARS) {
                return AgentToolCallValidation.Invalid("Tool arguments exceed the size limit.")
            }
            val arguments = try {
                JSONObject(argumentsText)
            } catch (_: JSONException) {
                return AgentToolCallValidation.Invalid("Tool arguments are not valid JSON.")
            }
            validated.add(ValidatedAgentToolCall(id, name, arguments))
        }
        return AgentToolCallValidation.Valid(validated.toList())
    }

    /**
     * Keep the provider-facing tool result valid JSON even when the original result is too large.
     * The preview is a partial serialized JSON string and is explicitly labelled as such.
     */
    fun boundedResult(result: JSONObject, maxChars: Int): String {
        require(maxChars >= MIN_RESULT_LIMIT_CHARS) { "Result limit must be at least $MIN_RESULT_LIMIT_CHARS characters." }
        val serialized = result.toString()
        if (serialized.length <= maxChars) return serialized

        val envelope = JSONObject()
            .put("truncated", true)
            .put("originalChars", serialized.length)
            .put("previewIsPartialJson", true)
            .put("message", "Tool result exceeded the limit. Preview is partial serialized JSON; do not treat it as a complete result.")
        var previewLength = minOf(serialized.length, maxChars / 2)
        while (previewLength > 0) {
            envelope.put("preview", serialized.take(previewLength))
            val bounded = envelope.toString()
            if (bounded.length <= maxChars) return bounded
            previewLength /= 2
        }
        envelope.remove("preview")
        val fallback = envelope.toString()
        check(fallback.length <= maxChars) { "Unable to fit truncation envelope within the result limit." }
        return fallback
    }
}
