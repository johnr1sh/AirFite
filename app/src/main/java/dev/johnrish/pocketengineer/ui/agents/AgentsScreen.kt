package dev.johnrish.pocketengineer.ui.agents

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.johnrish.pocketengineer.ChatViewModel
import dev.johnrish.pocketengineer.agent.CapabilityTier
import dev.johnrish.pocketengineer.ui.components.EmptyState
import dev.johnrish.pocketengineer.ui.components.ScreenHeader
import dev.johnrish.pocketengineer.ui.components.SectionLabel
import dev.johnrish.pocketengineer.ui.components.StatusDot
import org.json.JSONArray

private data class ToolSummary(val name: String, val description: String)

private data class TierRow(val tier: CapabilityTier, val title: String, val detail: String, val status: String, val available: Boolean)

/** Status here must mirror what is implemented; update it only when the capability really ships. */
private val tiers = listOf(
    TierRow(CapabilityTier.READ, "Read", "List, read and search files in the workspace", "Implemented", true),
    TierRow(CapabilityTier.PROPOSE, "Propose", "Generate patches and diffs without applying them", "Not implemented", false),
    TierRow(CapabilityTier.WRITE, "Write", "Create and modify files after per-action approval", "Not implemented", false),
    TierRow(CapabilityTier.DESTRUCTIVE, "Destructive", "Delete files; strong confirmation and backup", "Not implemented", false),
    TierRow(CapabilityTier.EXECUTE, "Execute", "Run commands, builds and tests", "Disabled", false)
)

private fun summarize(manifest: JSONArray): List<ToolSummary> = (0 until manifest.length()).mapNotNull { index ->
    manifest.optJSONObject(index)?.let { ToolSummary(it.optString("name"), it.optString("description")) }
}

@Composable
fun AgentsScreen(vm: ChatViewModel, modifier: Modifier = Modifier) {
    val tools = remember(vm.workspaceUri) { summarize(vm.workspaceToolManifest()) }
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item { ScreenHeader(title = "Agents", subtitle = "External agent connections") }
        item {
            EmptyState(
                icon = Icons.Outlined.Hub,
                title = "No agents connected",
                body = "AirFite will let external agents work in your workspace over MCP. The connection and " +
                    "permission model exists and is tested, but the MCP transport is not built yet, so nothing can connect today."
            )
        }
        item { SectionLabel("Tools AirFite can offer") }
        if (tools.isEmpty()) {
            item {
                Text(
                    "Choose a workspace in Files to see the tools available for it.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(tools) { tool ->
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(tool.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(tool.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { SectionLabel("Permission tiers") }
        items(tiers) { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                StatusDot(
                    if (row.available) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline,
                    Modifier.padding(top = 6.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(row.title, style = MaterialTheme.typography.bodyLarge)
                    Text(row.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(12.dp))
                Text(row.status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
