package dev.johnrish.pocketengineer.agent

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** A provider tool call after basic structural and schema validation. */
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
 * Only registered tools are accepted. The supported JSON Schema subset is deliberately small:
 * object properties, required keys, additionalProperties, primitive/container types, enum,
 * and string length bounds. Each workspace tool still performs its own authorization and
 * domain-specific validation.
 */
object AgentToolCallValidator {
    const val MAX_ID_LENGTH = 128
    const val MAX_TOOL_NAME_LENGTH = 128
    const val MAX_ARGUMENTS_CHARS = 16 * 1024
    const val MIN_RESULT_LIMIT_CHARS = 256
    const val MAX_SCHEMA_DEPTH = 8
    const val MAX_OBJECT_PROPERTIES = 64
    const val MAX_ARRAY_ITEMS = 128

    fun validate(
        calls: JSONArray,
        registeredToolSchemas: Map<String, JSONObject>,
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
            val rawId = call.opt("id") as? String
                ?: return AgentToolCallValidation.Invalid("Provider returned a tool call with an invalid ID.")
            val id = rawId.trim()
            if (id.isEmpty() || id.length > MAX_ID_LENGTH || rawId != id || id.any { it.code < 0x20 || it.code == 0x7F }) {
                return AgentToolCallValidation.Invalid("Provider returned a tool call with an invalid ID.")
            }
            if (!ids.add(id)) {
                return AgentToolCallValidation.Invalid("Provider returned duplicate tool-call IDs.")
            }

            val function = call.optJSONObject("function")
                ?: return AgentToolCallValidation.Invalid("Provider returned a tool call without a function.")
            val rawName = function.opt("name") as? String
                ?: return AgentToolCallValidation.Invalid("Provider returned a tool call with an invalid name.")
            val name = rawName.trim()
            if (name.isEmpty() || name.length > MAX_TOOL_NAME_LENGTH || rawName != name || name.any { it.code < 0x20 || it.code == 0x7F }) {
                return AgentToolCallValidation.Invalid("Provider returned a tool call with an invalid name.")
            }
            val schema = registeredToolSchemas[name]
                ?: return AgentToolCallValidation.Invalid("Provider requested an unavailable workspace tool.")

            val rawArguments = function.opt("arguments")
            if (rawArguments is String && rawArguments.length > MAX_ARGUMENTS_CHARS) {
                return AgentToolCallValidation.Invalid("Tool arguments exceed the size limit.")
            }
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
            val schemaError = validateArguments(arguments, schema)
            if (schemaError != null) return AgentToolCallValidation.Invalid(schemaError)
            validated.add(ValidatedAgentToolCall(id, name, arguments))
        }
        return AgentToolCallValidation.Valid(validated.toList())
    }

    private fun validateArguments(arguments: JSONObject, schema: JSONObject): String? =
        validateObject(arguments, schema, depth = 0)

    private fun validateObject(arguments: JSONObject, schema: JSONObject, depth: Int): String? {
        if (depth > MAX_SCHEMA_DEPTH) return "Tool arguments exceed the supported nesting depth."
        if (schema.optString("type") != "object") return "Registered tool has an unsupported argument schema."
        if (arguments.length() > MAX_OBJECT_PROPERTIES) return "Tool arguments contain too many object properties."

        if (schema.has("properties") && schema.optJSONObject("properties") == null) {
            return "Registered tool has an invalid object-property schema."
        }
        if (schema.has("required") && schema.optJSONArray("required") == null) {
            return "Registered tool has an invalid required-field schema."
        }
        if (schema.has("additionalProperties") && schema.opt("additionalProperties") !is Boolean) {
            return "Registered tool has an invalid additional-properties rule."
        }

        val properties = schema.optJSONObject("properties") ?: JSONObject()
        if (properties.length() > MAX_OBJECT_PROPERTIES) {
            return "Registered tool schema contains too many object properties."
        }
        val required = schema.optJSONArray("required") ?: JSONArray()
        for (index in 0 until required.length()) {
            val key = required.opt(index) as? String
                ?: return "Registered tool has an invalid required-field entry."
            if (key.isEmpty() || !arguments.has(key) || arguments.opt(key) == JSONObject.NULL) {
                return "Tool arguments are missing a required field."
            }
        }

        val propertyKeys = properties.keys()
        while (propertyKeys.hasNext()) {
            if (properties.optJSONObject(propertyKeys.next()) == null) {
                return "Registered tool has an invalid property definition."
            }
        }

        val allowAdditional = schema.optBoolean("additionalProperties", true)
        val keys = arguments.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = arguments.opt(key)
            val property = properties.optJSONObject(key)
            if (property == null) {
                if (!allowAdditional) return "Tool arguments contain an unsupported field."
                continue
            }
            val valueError = validateValue(value, property, depth + 1)
            if (valueError != null) return valueError
        }
        return null
    }

    /** Validate supported JSON Schema constraints recursively without coercing provider input. */
    private fun validateValue(value: Any?, schema: JSONObject, depth: Int): String? {
        if (!matchesType(value, schema.optString("type"))) {
            return "Tool argument type does not match the registered schema."
        }
        if (value is String) {
            val minLength = schema.optInt("minLength", 0)
            val maxLength = schema.optInt("maxLength", Int.MAX_VALUE)
            if (minLength < 0 || maxLength < minLength) return "Registered tool has invalid string-length constraints."
            if (value.length < minLength || value.length > maxLength) {
                return "Tool argument string length is outside the registered bounds."
            }
        }
        if (value is Number) {
            val number = value.toDouble()
            if (!number.isFinite()) return "Tool argument number must be finite."
            if (schema.has("minimum") && (!schema.optDouble("minimum").isFinite() || number < schema.optDouble("minimum"))) {
                return "Tool argument number is below the registered minimum."
            }
            if (schema.has("maximum") && (!schema.optDouble("maximum").isFinite() || number > schema.optDouble("maximum"))) {
                return "Tool argument number is above the registered maximum."
            }
        }
        if (value is JSONObject) {
            val nestedError = validateObject(value, schema, depth)
            if (nestedError != null) return nestedError
        }
        if (value is JSONArray) {
            if (depth > MAX_SCHEMA_DEPTH) return "Tool arguments exceed the supported nesting depth."
            val minItems = schema.optInt("minItems", 0)
            val declaredMaxItems = schema.optInt("maxItems", MAX_ARRAY_ITEMS)
            if (minItems < 0 || declaredMaxItems < minItems) {
                return "Registered tool has invalid array-size constraints."
            }
            val maxItems = minOf(declaredMaxItems, MAX_ARRAY_ITEMS)
            if (value.length() < minItems || value.length() > maxItems) {
                return "Tool argument array length is outside the registered bounds."
            }
            val itemSchema = schema.optJSONObject("items")
            if (schema.has("items") && itemSchema == null) {
                return "Registered tool has an invalid array-item schema."
            }
            if (itemSchema != null) {
                for (index in 0 until value.length()) {
                    if (validateValue(value.opt(index), itemSchema, depth + 1) != null) {
                        return "Tool argument array contains an invalid item."
                    }
                }
            }
        }
        val enumValues = schema.optJSONArray("enum")
        if (enumValues != null && !containsValue(enumValues, value)) {
            return "Tool argument is not one of the allowed values."
        }
        return null
    }

    private fun matchesType(value: Any?, expected: String): Boolean = when (expected) {
        "string" -> value is String
        "boolean" -> value is Boolean
        "integer" -> value is Number && value.toDouble().isFinite() && value.toDouble() % 1.0 == 0.0
        "number" -> value is Number && value.toDouble().isFinite()
        "object" -> value is JSONObject
        "array" -> value is JSONArray
        else -> false
    }

    private fun containsValue(values: JSONArray, value: Any?): Boolean =
        (0 until values.length()).any { index -> values.opt(index) == value }

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
