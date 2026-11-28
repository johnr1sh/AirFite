package dev.johnrish.pocketengineer

import dev.johnrish.pocketengineer.workspace.SensitiveFileGuard
import dev.johnrish.pocketengineer.workspace.WorkspacePath
import org.json.JSONObject

/**
 * A bounded, read-only patch proposal surfaced to the user for review.
 *
 * This model intentionally carries no write handle or apply operation. A proposal is only a
 * preview produced by the workspace tool service; it is not an approved or applied change.
 */
data class WorkspacePatchProposal(
    val id: String,
    val path: String,
    val rationale: String,
    val lineNumber: Int,
    val before: String,
    val after: String,
    val diffPreview: String,
    val baseSha256: String,
    val proposedSha256: String
) {
    companion object {
        const val MAX_SNIPPET_CHARS = 4 * 1024
        const val MAX_DIFF_PREVIEW_CHARS = 16 * 1024
        const val MAX_RATIONALE_CHARS = 400
        private val SHA256_PATTERN = Regex("^[a-f0-9]{64}$")

        /**
         * Parse only an explicit, unapplied proposal result. Invalid or oversized tool output is
         * ignored rather than being presented as an actionable change.
         */
        fun fromToolResult(result: JSONObject): WorkspacePatchProposal? {
            if (result.optString("status") != "proposal_only" || result.optBoolean("applied", true)) {
                return null
            }

            val path = result.optString("path").takeIf(String::isNotBlank) ?: return null
            if (WorkspacePath.parseOrNull(path)?.value != path) return null
            if (runCatching { SensitiveFileGuard.requireSafePath(path) }.isFailure) return null

            val rationale = result.optString("rationale").trim()
            val lineNumber = result.optInt("lineNumber", 0)
            val before = result.optString("before")
            val after = result.optString("after")
            val diffPreview = result.optString("diffPreview")
            val baseSha256 = result.optString("baseSha256")
            val proposedSha256 = result.optString("proposedSha256")

            if (rationale.isEmpty() || rationale.length > MAX_RATIONALE_CHARS ||
                lineNumber <= 0 || before.isEmpty() || before.length > MAX_SNIPPET_CHARS ||
                after.length > MAX_SNIPPET_CHARS || before == after ||
                diffPreview.isEmpty() || diffPreview.length > MAX_DIFF_PREVIEW_CHARS ||
                !SHA256_PATTERN.matches(baseSha256) || !SHA256_PATTERN.matches(proposedSha256) ||
                baseSha256 == proposedSha256
            ) {
                return null
            }

            return WorkspacePatchProposal(
                id = "$path:$baseSha256:$proposedSha256",
                path = path,
                rationale = rationale,
                lineNumber = lineNumber,
                before = before,
                after = after,
                diffPreview = diffPreview,
                baseSha256 = baseSha256,
                proposedSha256 = proposedSha256
            )
        }
    }
}
