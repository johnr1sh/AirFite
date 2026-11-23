package dev.johnrish.pocketengineer.agent

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentToolCallValidatorTest {
    private val allowed = setOf("workspace.list_files", "workspace.read_file", "workspace.search_files")

    private fun call(id: String = "call_1", name: String = "workspace.read_file", arguments: String = """{"path":"src/Main.kt"}""") =
        JSONObject().put("id", id).put("type", "function").put(
            "function", JSONObject().put("name", name).put("arguments", arguments)
        )

    @Test
    fun acceptsRegisteredCallWithObjectArguments() {
        val result = AgentToolCallValidator.validate(
            JSONArray().put(call()), allowed, maxCalls = 8
        )
        assertTrue(result is AgentToolCallValidation.Valid)
        val parsed = (result as AgentToolCallValidation.Valid).calls.single()
        assertEquals("call_1", parsed.id)
        assertEquals("workspace.read_file", parsed.name)
        assertEquals("src/Main.kt", parsed.arguments.getString("path"))
    }

    @Test
    fun acceptsProviderObjectArgumentsAndEmptyArguments() {
        val objectArgs = call().put("function", JSONObject().put("name", "workspace.read_file")
            .put("arguments", JSONObject().put("path", "README.md")))
        val emptyArgs = call("call_2", "workspace.list_files", "")
        val result = AgentToolCallValidator.validate(JSONArray().put(objectArgs).put(emptyArgs), allowed, 8)
        assertTrue(result is AgentToolCallValidation.Valid)
        val parsed = (result as AgentToolCallValidation.Valid).calls
        assertEquals("README.md", parsed[0].arguments.getString("path"))
        assertEquals(0, parsed[1].arguments.length())
    }

    @Test
    fun rejectsMissingOrInvalidIds() {
        val missing = call().put("id", "")
        val tooLong = call("x".repeat(AgentToolCallValidator.MAX_ID_LENGTH + 1))
        val control = call("call_1\n")
        listOf(missing, tooLong, control).forEach {
            val result = AgentToolCallValidator.validate(JSONArray().put(it), allowed, 8)
            assertTrue(result is AgentToolCallValidation.Invalid)
        }
    }

    @Test
    fun rejectsDuplicateIds() {
        val result = AgentToolCallValidator.validate(JSONArray().put(call()).put(call()), allowed, 8)
        assertEquals("Provider returned duplicate tool-call IDs.", (result as AgentToolCallValidation.Invalid).reason)
    }

    @Test
    fun rejectsUnknownToolName() {
        val result = AgentToolCallValidator.validate(
            JSONArray().put(call(name = "workspace.delete_file")), allowed, 8
        )
        assertEquals("Provider requested an unavailable workspace tool.", (result as AgentToolCallValidation.Invalid).reason)
    }

    @Test
    fun rejectsMissingFunctionAndName() {
        val noFunction = JSONObject().put("id", "call_1")
        val noName = call().put("function", JSONObject().put("arguments", "{}"))
        listOf(noFunction, noName).forEach {
            assertTrue(AgentToolCallValidator.validate(JSONArray().put(it), allowed, 8) is AgentToolCallValidation.Invalid)
        }
    }

    @Test
    fun rejectsMalformedAndNonObjectArguments() {
        val malformed = call(arguments = """{"path":""")
        val arrayRoot = call(arguments = """["not","an","object"]""")
        val nonString = call().put("function", JSONObject().put("name", "workspace.read_file").put("arguments", JSONArray()))
        listOf(malformed, arrayRoot, nonString).forEach {
            assertTrue(AgentToolCallValidator.validate(JSONArray().put(it), allowed, 8) is AgentToolCallValidation.Invalid)
        }
    }

    @Test
    fun rejectsOversizedArgumentsAndTooManyCalls() {
        val oversized = call(arguments = " ".repeat(AgentToolCallValidator.MAX_ARGUMENTS_CHARS + 1))
        assertTrue(AgentToolCallValidator.validate(JSONArray().put(oversized), allowed, 8) is AgentToolCallValidation.Invalid)
        val result = AgentToolCallValidator.validate(
            JSONArray().put(call("a")).put(call("b")), allowed, maxCalls = 1
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
}
