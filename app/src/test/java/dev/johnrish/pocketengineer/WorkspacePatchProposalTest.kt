package dev.johnrish.pocketengineer

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class WorkspacePatchProposalTest {
    private fun proposalResult(): JSONObject = JSONObject()
        .put("status", "proposal_only")
        .put("applied", false)
        .put("path", "src/Main.kt")
        .put("rationale", "Fix the null handling.")
        .put("lineNumber", 12)
        .put("before", "val name = user.name\n")
        .put("after", "val name = user?.name.orEmpty()\n")
        .put("diffPreview", "--- a/src/Main.kt\n+++ b/src/Main.kt\n- val name = user.name\n+ val name = user?.name.orEmpty()\n")
        .put("baseSha256", "a".repeat(64))
        .put("proposedSha256", "b".repeat(64))
        .put("message", "Preview only. No workspace file was changed.")

    @Test
    fun parsesValidProposalAndBuildsStableIdentity() {
        val proposal = WorkspacePatchProposal.fromToolResult(proposalResult())

        assertNotNull(proposal)
        assertEquals("src/Main.kt", proposal?.path)
        assertEquals(12, proposal?.lineNumber)
        assertEquals("val name = user.name\n", proposal?.before)
        assertEquals("val name = user?.name.orEmpty()\n", proposal?.after)
        assertEquals("src/Main.kt:" + "a".repeat(64) + ":" + "b".repeat(64), proposal?.id)
    }

    @Test
    fun rejectsAppliedOrUnrecognizedResults() {
        assertNull(WorkspacePatchProposal.fromToolResult(proposalResult().put("applied", true)))
        assertNull(WorkspacePatchProposal.fromToolResult(proposalResult().put("status", "applied")))
        assertNull(WorkspacePatchProposal.fromToolResult(JSONObject()))
    }

    @Test
    fun rejectsUnsafeOrSensitivePaths() {
        assertNull(WorkspacePatchProposal.fromToolResult(proposalResult().put("path", "../secrets.txt")))
        assertNull(WorkspacePatchProposal.fromToolResult(proposalResult().put("path", ".env")))
        assertNull(WorkspacePatchProposal.fromToolResult(proposalResult().put("path", "src//Main.kt")))
    }

    @Test
    fun rejectsInvalidHashesAndNoOpChanges() {
        assertNull(WorkspacePatchProposal.fromToolResult(proposalResult().put("baseSha256", "not-a-hash")))
        assertNull(WorkspacePatchProposal.fromToolResult(proposalResult().put("proposedSha256", "a".repeat(64))))
        assertNull(WorkspacePatchProposal.fromToolResult(proposalResult().put("after", "val name = user.name\n")))
    }

    @Test
    fun rejectsCredentialContentInTheRemovedSideOfADiff() {
        val result = proposalResult()
            .put("before", "api_key = a-real-looking-secret-value\n")
            .put("after", "api_key = \"\"\n")
            .put("diffPreview", "- api_key = a-real-looking-secret-value\n+ api_key = \"\"\n")

        assertNull(WorkspacePatchProposal.fromToolResult(result))
    }

    @Test
    fun rejectsInvalidLineNumbersAndOversizedFields() {
        assertNull(WorkspacePatchProposal.fromToolResult(proposalResult().put("lineNumber", 0)))
        assertNull(
            WorkspacePatchProposal.fromToolResult(
                proposalResult().put("diffPreview", "x".repeat(WorkspacePatchProposal.MAX_DIFF_PREVIEW_CHARS + 1))
            )
        )
        assertNull(
            WorkspacePatchProposal.fromToolResult(
                proposalResult().put("before", "x".repeat(WorkspacePatchProposal.MAX_SNIPPET_CHARS + 1))
            )
        )
    }
}
