package dev.johnrish.pocketengineer.ui.agents

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.work.WorkManager
import dev.johnrish.pocketengineer.ChatViewModel
import dev.johnrish.pocketengineer.MainActivity
import dev.johnrish.pocketengineer.SecureKeyStore
import dev.johnrish.pocketengineer.agent.CapabilityTier
import dev.johnrish.pocketengineer.background.BackgroundAgentWorker
import dev.johnrish.pocketengineer.background.BackgroundTaskManager
import dev.johnrish.pocketengineer.background.BackgroundTaskRecord
import dev.johnrish.pocketengineer.background.BackgroundTaskStore
import dev.johnrish.pocketengineer.ui.components.EmptyState
import dev.johnrish.pocketengineer.ui.components.ScreenHeader
import dev.johnrish.pocketengineer.ui.components.SectionLabel
import dev.johnrish.pocketengineer.ui.components.StatusDot
import kotlinx.coroutines.delay
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
    val context = LocalContext.current
    val tools = remember(vm.workspaceUri) { summarize(vm.workspaceToolManifest()) }
    var prompt by rememberSaveable { mutableStateOf("") }
    var records by remember { mutableStateOf(BackgroundTaskStore.list(context)) }
    val initialTaskId = (context as? MainActivity)?.intent?.getStringExtra(BackgroundAgentWorker.EXTRA_TASK_ID)
    var selected by remember { mutableStateOf(BackgroundTaskStore.list(context).firstOrNull { it.id == initialTaskId }) }
    var taskResult by remember { mutableStateOf("") }
    var taskError by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }

    fun enqueueTask() {
        val currentPrompt = prompt.trim()
        if (currentPrompt.isBlank() || submitting) return
        if (!vm.saveSettings()) {
            taskError = vm.settingsError ?: "Check your provider settings."
            return
        }
        submitting = true
        runCatching {
            BackgroundTaskManager.enqueue(
                context = context,
                prompt = currentPrompt,
                endpoint = vm.baseUrl,
                model = vm.model,
                encryptedApiKey = SecureKeyStore().encrypt(vm.apiKey)
            )
        }.onSuccess {
            prompt = ""
            taskError = null
            records = BackgroundTaskStore.list(context)
            Toast.makeText(context, "Task queued for background execution.", Toast.LENGTH_SHORT).show()
        }.onFailure { taskError = it.message ?: "Unable to queue task." }
        submitting = false
    }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Tasks can run without notification permission; results remain in this screen.
        enqueueTask()
    }

    LaunchedEffect(Unit) {
        while (true) {
            records = BackgroundTaskStore.list(context)
            delay(2500)
        }
    }

    if (selected != null) {
        val record = selected!!
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(BackgroundTaskStore.decryptTitle(record).ifBlank { "Background task" }) },
            text = {
                Column(Modifier.height(420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(record.status, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    if (record.error.isNotBlank()) Text(record.error, color = MaterialTheme.colorScheme.error)
                    if (record.encryptedResult.isNotBlank()) Text(BackgroundTaskStore.decryptResult(record), style = MaterialTheme.typography.bodyMedium)
                    else Text("The response will appear here when the task completes.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("Close") } }
        )
    }

    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { ScreenHeader(title = "Agents", subtitle = "Capabilities and background runs") }
        item {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.width(8.dp))
                        Text("MCP connection", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("No external agents connected", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 10.dp))
                    Text("External MCP connections are not available yet. Workspace tools and background runs work independently.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        item { SectionLabel("Run in background") }
        item {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Task prompt") },
                        placeholder = { Text("Ask AirFite to research, plan, or analyze…") },
                        minLines = 3,
                        maxLines = 6
                    )
                    Text("Runs through WorkManager, survives app closure, retries network failures, and stores its response encrypted on-device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    taskError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Button(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else enqueueTask()
                        },
                        enabled = prompt.isNotBlank() && !submitting,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (submitting) CircularProgressIndicator(Modifier.width(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (submitting) "Queueing…" else "Run in background")
                    }
                }
            }
        }
        item { SectionLabel("Task history") }
        if (records.isEmpty()) {
            item { EmptyState(icon = Icons.Outlined.Hub, title = "No background tasks", body = "Queue a task above. It can continue when you leave AirFite.") }
        } else {
            items(records, key = { it.id }) { record ->
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(BackgroundTaskStore.decryptTitle(record).ifBlank { "Background task" }, maxLines = 2, style = MaterialTheme.typography.titleSmall)
                            Text(record.status + " · " + java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(record.createdAt)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                            if (record.error.isNotBlank()) Text(record.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
                        }
                        TextButton(onClick = { selected = record }) { Text(if (record.encryptedResult.isNotBlank()) "View" else "Details") }
                        IconButton(onClick = {
                            if (record.status == "Queued" || record.status == "Running" || record.status == "Retrying") BackgroundTaskManager.cancel(context, record.id)
                            else BackgroundTaskStore.delete(context, record.id)
                            records = BackgroundTaskStore.list(context)
                        }) { Icon(Icons.Outlined.Delete, contentDescription = "Cancel or delete task") }
                    }
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
