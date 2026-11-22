package dev.johnrish.pocketengineer.agent

/** Independent capability grants. Holding one tier never implies another (e.g. WRITE does not imply READ). */
enum class CapabilityTier { READ, PROPOSE, WRITE, DESTRUCTIVE, EXECUTE }

/** What a tool needs from the grant set. [id] is the stable identifier agents see. */
data class ToolSpec(val id: String, val tier: CapabilityTier)

enum class ApprovalLevel {
    /** User confirms each action. */
    PER_ACTION,

    /** User confirms with an explicit, hard-to-miss prompt; a backup must exist before it runs. */
    STRONG
}

sealed interface PolicyDecision {
    data object Allow : PolicyDecision
    data class RequiresApproval(val level: ApprovalLevel) : PolicyDecision
    data class Deny(val reason: String) : PolicyDecision
}

/**
 * Central, deny-by-default gate evaluated before every tool invocation. Pure: callers pass the
 * live connection and the grant set for (connection, project); nothing is cached, so revoking a
 * grant or connection takes effect on the very next call.
 *
 * [executeEnabled] stays false until command execution has a reviewed isolation story
 * (see docs/AGENT_CONNECTION_ARCHITECTURE.md); EXECUTE is then denied even if "granted".
 */
class ToolPolicy(
    private val registeredTools: Set<String>,
    private val executeEnabled: Boolean = false
) {
    fun decide(connection: ConnectionSession, grants: Set<CapabilityTier>, tool: ToolSpec): PolicyDecision {
        if (tool.id !in registeredTools) return PolicyDecision.Deny("Unknown tool.")
        if (!connection.isLive) return PolicyDecision.Deny("Agent connection is not active.")
        if (tool.tier == CapabilityTier.EXECUTE && !executeEnabled) {
            return PolicyDecision.Deny("Command execution is not available yet.")
        }
        if (tool.tier !in grants) return PolicyDecision.Deny("This agent has not been granted ${tool.tier.name.lowercase()} access.")
        return when (tool.tier) {
            CapabilityTier.READ, CapabilityTier.PROPOSE -> PolicyDecision.Allow
            CapabilityTier.WRITE -> PolicyDecision.RequiresApproval(ApprovalLevel.PER_ACTION)
            CapabilityTier.DESTRUCTIVE -> PolicyDecision.RequiresApproval(ApprovalLevel.STRONG)
            CapabilityTier.EXECUTE -> PolicyDecision.RequiresApproval(ApprovalLevel.STRONG)
        }
    }
}
