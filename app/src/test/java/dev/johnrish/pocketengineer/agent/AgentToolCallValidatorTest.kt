package dev.johnrish.pocketengineer.agent

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentToolCallValidatorTest {
    private fun schema(properties: JSONObject = JSONObject(), required: JSONArray = JSONArray()) =
        JSONObject().put("type", "object").put("properties", properties)
            .put("required", required).put("additionalProperties", false)

    private val schemas = mapOf(
        "workspace.list_files" to schema(),
        "workspace.read_file" to schema(
            JSONObject().put("path", JSONObject().put("type", "string")),
            JSONArray().put("path")
        ),
        "workspace.search_files" to schema(
            JSONObject().put("query", JSONObject().put("type", "string")),
            JSONArray().put("query")
        )
    )

    private fun call(
        id: String = "call_1",
        name: String = "workspace.read_file",
        arguments: String = """{"path":"src/Main.kt"}"""
    ) = JSONObject().put("id", id).put("type", "function").put(
        "function", JSONObject().put("name", name).put("arguments", arguments)
    )

    @Test
    fun acceptsRegisteredCallWithObjectArguments() {
        val result = AgentToolCallValidator.validate(
            JSONArray().put(call()), schemas, maxCalls = 8
        )
        assertTrue(result is AgentToolCallValidation.Valid)
        val parsed = (result as AgentToolCallValidation.Valid).calls.single()
        assertEquals("call_1", parsed.id)
        assertEquals("workspace.read_file", parsed.name)
        assertEquals("src/Main.kt", parsed.arguments.getString("path"))
    }

    @Test
    fun acceptsProviderObjectArgumentsAndEmptyArgumentsForNoArgTool() {
        val objectArgs = call().put("function", JSONObject().put("name", "workspace.read_file")
            .put("arguments", JSONObject().put("path", "README.md")))
        val emptyArgs = call("call_2", "workspace.list_files", "")
        val result = AgentToolCallValidator.validate(JSONArray().put(objectArgs).put(emptyArgs), schemas, 8)
        assertTrue(result is AgentToolCallValidation.Valid)
        val parsed = (result as AgentToolCallValidation.Valid).calls
        assertEquals("README.md", parsed[0].arguments.getString("path"))
        assertEquals(0, parsed[1].arguments.length())
    }

    @Test
    fun rejectsMissingOrInvalidIdsAndNames() {
        val missing = call().put("id", "")
        val tooLong = call("x".repeat(AgentToolCallValidator.MAX_ID_LENGTH + 1))
        val control = call("call_1\n")
        val paddedId = call(" call_1 ")
        val paddedName = call().put("function", JSONObject().put("name", " workspace.read_file ").put("arguments", "{}"))
        listOf(missing, tooLong, control, paddedId, paddedName).forEach {
            val result = AgentToolCallValidator.validate(JSONArray().put(it), schemas, 8)
            assertTrue(result is AgentToolCallValidation.Invalid)
        }
    }

    @Test
    fun rejectsDuplicateIds() {
        val result = AgentToolCallValidator.validate(JSONArray().put(call()).put(call()), schemas, 8)
        assertEquals("Provider returned duplicate tool-call IDs.", (result as AgentToolCallValidation.Invalid).reason)
    }

    @Test
    fun rejectsUnknownToolName() {
        val result = AgentToolCallValidator.validate(
            JSONArray().put(call(name = "workspace.delete_file")), schemas, 8
        )
        assertEquals("Provider requested an unavailable workspace tool.", (result as AgentToolCallValidation.Invalid).reason)
    }

    @Test
    fun rejectsMissingFunctionAndName() {
        val noFunction = JSONObject().put("id", "call_1")
        val noName = call().put("function", JSONObject().put("arguments", "{}"))
        listOf(noFunction, noName).forEach {
            assertTrue(AgentToolCallValidator.validate(JSONArray().put(it), schemas, 8) is AgentToolCallValidation.Invalid)
        }
    }

    @Test
    fun rejectsMalformedAndNonObjectArguments() {
        val malformed = call(arguments = """{"path":""")
        val arrayRoot = call(arguments = """["not","an","object"]""")
        val nonString = call().put("function", JSONObject().put("name", "workspace.read_file").put("arguments", JSONArray()))
        listOf(malformed, arrayRoot, nonString).forEach {
            assertTrue(AgentToolCallValidator.validate(JSONArray().put(it), schemas, 8) is AgentToolCallValidation.Invalid)
        }
    }

    @Test
    fun rejectsMissingRequiredFieldsWrongTypesAndExtraProperties() {
        val missingRequired = call(arguments = "{}")
        val wrongType = call(name = "workspace.search_files", arguments = """{"query":42}""")
        val extraProperty = call(arguments = """{"path":"src/Main.kt","unexpected":true}""")
        listOf(missingRequired, wrongType, extraProperty).forEach {
            assertTrue(AgentToolCallValidator.validate(JSONArray().put(it), schemas, 8) is AgentToolCallValidation.Invalid)
        }
    }

    @Test
    fun rejectsOversizedArgumentsAndTooManyCalls() {
        val oversized = call(arguments = " ".repeat(AgentToolCallValidator.MAX_ARGUMENTS_CHARS + 1))
        assertTrue(AgentToolCallValidator.validate(JSONArray().put(oversized), schemas, 8) is AgentToolCallValidation.Invalid)
        val result = AgentToolCallValidator.validate(
            JSONArray().put(call("a")).put(call("b")), schemas, maxCalls = 1
        )
        assertEquals("Provider requested too many tools in one round.", (result as AgentToolCallValidation.Invalid).reason)
    }

    @Test
    fun boundedResultKeepsSmallResultsUnchanged() {
        val result = JSONObject().put("matches", JSONArray().put("README.md"))
        assertEquals(result.toString(), AgentToolCallValidator.boundedResult(result, 1024))
    }

    @Test
    fun oversizedResultIsValidJsonAndWithinLimit() {
        val limit = 512
        val result = JSONObject().put("content", "line \"value\"\n".repeat(200))
        val bounded = AgentToolCallValidator.boundedResult(result, limit)
        assertTrue(bounded.length <= limit)
        val parsed = JSONObject(bounded)
        assertTrue(parsed.getBoolean("truncated"))
        assertTrue(parsed.getBoolean("previewIsPartialJson"))
        assertTrue(parsed.getInt("originalChars") > parsed.getString("preview").length)
        assertFalse(parsed.getString("message").isBlank())
    }

    @Test
    fun enforcesNumericBounds() {
        val schema = schema(
            JSONObject().put("count", JSONObject().put("type", "integer").put("minimum", 1).put("maximum", 5)),
            JSONArray().put("count")
        )
        val tools = mapOf("test.bounded" to schema)
        fun callFor(value: String) = call(name = "test.bounded", arguments = """{"count":$value}""")
        assertTrue(AgentToolCallValidator.validate(JSONArray().put(callFor("1")), tools, 1) is AgentToolCallValidation.Valid)
        assertTrue(AgentToolCallValidator.validate(JSONArray().put(callFor("5")), tools, 1) is AgentToolCallValidation.Valid)
        assertTrue(AgentToolCallValidator.validate(JSONArray().put(callFor("0")), tools, 1) is AgentToolCallValidation.Invalid)
        assertTrue(AgentToolCallValidator.validate(JSONArray().put(callFor("6")), tools, 1) is AgentToolCallValidation.Invalid)
    }

    @Test
    fun enforcesArrayBoundsAndItemTypesAndLengths() {
        val schema = schema(
            JSONObject().put("labels", JSONObject().put("type", "array")
                .put("minItems", 1).put("maxItems", 2)
                .put("items", JSONObject().put("type", "string").put("maxLength", 4))),
            JSONArray().put("labels")
        )
        val tools = mapOf("test.labels" to schema)
        fun callFor(args: String) = call(name = "test.labels", arguments = args)
        assertTrue(AgentToolCallValidator.validate(JSONArray().put(callFor("""{"labels":["ui","api"]}""")), tools, 1) is AgentToolCallValidation.Valid)
        assertTrue(AgentToolCallValidator.validate(JSONArray().put(callFor("""{"labels":[]}""")), tools, 1) is AgentToolCallValidation.Invalid)
        assertTrue(AgentToolCallValidator.validate(JSONArray().put(callFor("""{"labels":["a","b","c"]}""")), tools, 1) is AgentToolCallValidation.Invalid)
        assertTrue(AgentToolCallValidator.validate(JSONArray().put(callFor("""{"labels":["toolong"]}""")), tools, 1) is AgentToolCallValidation.Invalid)
    }


    @Test
    fun recursivelyValidatesNestedObjectRequiredFieldsAndAdditionalProperties() {
        val profileSchema = JSONObject().put("type", "object")
            .put("properties", JSONObject()
                .put("displayName", JSONObject().put("type", "string").put("minLength", 1))
                .put("age", JSONObject().put("type", "integer").put("minimum", 0).put("maximum", 130)))
            .put("required", JSONArray().put("displayName"))
            .put("additionalProperties", false)
        val tools = mapOf(
            "test.profile" to schema(
                JSONObject().put("profile", profileSchema),
                JSONArray().put("profile")
            )
        )
        fun callFor(arguments: JSONObject) = call(name = "test.profile").put(
            "function", JSONObject().put("name", "test.profile").put("arguments", arguments)
        )

        val valid = JSONObject().put("profile", JSONObject().put("displayName", "AirFite").put("age", 1))
        assertTrue(AgentToolCallValidator.validate(JSONArray().put(callFor(valid)), tools, 1) is AgentToolCallValidation.Valid)

        val missingNestedRequired = JSONObject().put("profile", JSONObject().put("age", 1))
        assertTrue(AgentToolCallValidator.validate(JSONArray().put(callFor(missingNestedRequired)), tools, 1) is AgentToolCallValidation.Invalid)

        val unexpectedNestedField = JSONObject().put(
            "profile", JSONObject().put("displayName", "AirFite").put("admin", true)
        )
        assertTrue(AgentToolCallValidator.validate(JSONArray().put(callFor(unexpectedNestedField)), tools, 1) is AgentToolCallValidation.Invalid)

        val wrongNestedType = JSONObject().put("profile", JSONObject().put("displayName", 42))
        assertTrue(AgentToolCallValidator.validate(JSONArray().put(callFor(wrongNestedType)), tools, 1) is AgentToolCallValidation.Invalid)
    }

    @Test
    fun recursivelyValidatesObjectSchemasInsideArrays() {
        val itemSchema = JSONObject().put("type", "object")
            .put("properties", JSONObject()
                .put("path", JSONObject().put("type", "string").put("minLength", 1))
                .put("line", JSONObject().put("type", "integer").put("minimum", 1)))
            .put("required", JSONArray().put("path").put("line"))
            .put("additionalProperties", false)
        val tools = mapOf(
            "test.edits" to schema(
                JSONObject().put("edits", JSONObject().put("type", "array")
                    .put("minItems", 1).put("maxItems", 2).put("items", itemSchema)),
                JSONArray().put("edits")
            )
        )
        fun callFor(arguments: String) = call(name = "test.edits", arguments = arguments)

        assertTrue(AgentToolCallValidator.validate(
            JSONArray().put(callFor("""{"edits":[{"path":"src/Main.kt","line":1}]}""")), tools, 1
        ) is AgentToolCallValidation.Valid)
        assertTrue(AgentToolCallValidator.validate(
            JSONArray().put(callFor("""{"edits":[{"path":"src/Main.kt"}]}""")), tools, 1
        ) is AgentToolCallValidation.Invalid)
        assertTrue(AgentToolCallValidator.validate(
            JSONArray().put(callFor("""{"edits":[{"path":"src/Main.kt","line":1,"force":true}]}""")), tools, 1
        ) is AgentToolCallValidation.Invalid)
        assertTrue(AgentToolCallValidator.validate(
            JSONArray().put(callFor("""{"edits":[{"path":"src/Main.kt","line":"1"}]}""")), tools, 1
        ) is AgentToolCallValidation.Invalid)
    }

    @Test
    fun rejectsArgumentsNestedBeyondTheConfiguredDepth() {
        var nestedSchema = JSONObject().put("type", "object")
            .put("properties", JSONObject().put("leaf", JSONObject().put("type", "string")))
            .put("required", JSONArray().put("leaf"))
            .put("additionalProperties", false)
        var nestedArguments = JSONObject().put("leaf", "value")
        repeat(AgentToolCallValidator.MAX_SCHEMA_DEPTH + 1) {
            nestedSchema = JSONObject().put("type", "object")
                .put("properties", JSONObject().put("next", nestedSchema))
                .put("required", JSONArray().put("next"))
                .put("additionalProperties", false)
            nestedArguments = JSONObject().put("next", nestedArguments)
        }
        val tools = mapOf(
            "test.deep" to schema(
                JSONObject().put("payload", nestedSchema),
                JSONArray().put("payload")
            )
        )
        val deepCall = call(name = "test.deep").put(
            "function", JSONObject().put("name", "test.deep")
                .put("arguments", JSONObject().put("payload", nestedArguments))
        )

        val result = AgentToolCallValidator.validate(JSONArray().put(deepCall), tools, 1)
        assertTrue(result is AgentToolCallValidation.Invalid)
        assertEquals(
            "Tool arguments exceed the supported nesting depth.",
            (result as AgentToolCallValidation.Invalid).reason
        )
    }

}
