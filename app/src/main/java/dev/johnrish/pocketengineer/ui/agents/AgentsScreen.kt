
package dev.johnrish.pocketengineer.ui.agents

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Surface
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

private val tiers = listOf(
    TierRow(CapabilityTier.READ, "Read", "List, read and search files in the workspace", "Available", true),
    TierRow(CapabilityTier.PROPOSE, "Propose", "Generate patches and diffs without applying them", "Planned", false),
    TierRow(CapabilityTier.WRITE, "Write", "Create and modify files after per-action approval", "Planned", false),
    TierRow(CapabilityTier.DESTRUCTIVE, "Destructive", "Delete files; strong confirmation and backup", "Planned", false),
    TierRow(CapabilityTier.EXECUTE, "Execute", "Run commands, builds and tests", "Disabled", false)
)

private fun summarize(manifest: JSONArray): List<ToolSummary> = (0 until manifest.length()).mapNotNull { index ->
    manifest.optJSONObject(index)?.let { ToolSummary(it.optString("name"), it.optString("description")) }
}

@Composable
fun AgentsScreen(vm: ChatViewModel, modifier: Modifier = Modifier) {
    val tools = remember(vm.workspaceUri) { summarize(vm.workspaceToolManifest()) }
    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { ScreenHeader(title = "Agents", subtitle = "Connections and capabilities") }
        item {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.width(8.dp))
                        Text("MCP connection", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("No agents connected", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 10.dp))
                    Text(
                        "External agent connections are not available yet. The permission model is being prepared; transport support is still in development.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
        item { SectionLabel("Workspace tools") }
        if (tools.isEmpty()) {
            item {
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
                    Text("Choose a workspace in Files to preview the tools AirFite can offer.", Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(tools) { tool ->
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text(tool.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Text(tool.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
        item { SectionLabel("Permission levels") }
        items(tiers) { row ->
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(if (row.available) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(row.title, style = MaterialTheme.typography.titleSmall)
                        Text(row.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 3.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(row.status, style = MaterialTheme.typography.labelSmall, color = if (row.available) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
