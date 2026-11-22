package dev.johnrish.pocketengineer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AirFiteApp() }
    }
}

private val ink = Color(0xFF101318)
private val panel = Color(0xFF191D24)
private val muted = Color(0xFF9AA4B2)
private val accent = Color(0xFF9BDB8A)

@Composable
fun AirFiteApp(vm: ChatViewModel = viewModel()) {
    var showSettings by remember { mutableStateOf(false) }
    var showWorkspace by remember { mutableStateOf(false) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                vm.getApplication<android.app.Application>().contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            vm.setWorkspace(uri)
        }
    }
    MaterialTheme(colorScheme = darkColorScheme(background = ink, surface = panel, primary = accent,
        onBackground = Color(0xFFE9EDF2), onSurface = Color(0xFFE9EDF2))) {
        Surface(Modifier.fillMaxSize(), color = ink) {
            when {
                showSettings -> SettingsScreen(vm) { showSettings = false }
                showWorkspace -> WorkspaceScreen(vm, onPickFolder = { folderPicker.launch(null) }, onBack = { showWorkspace = false })
                else -> Column(Modifier.fillMaxSize().padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Code, null, tint = accent)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("AirFite", fontWeight = FontWeight.Bold)
                                Text("Mobile coding assistant", color = muted, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Row {
                            IconButton(onClick = { showWorkspace = true }) { Icon(Icons.Default.FolderOpen, "Workspace") }
                            IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, "Settings") }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("What are we building?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        if (vm.messages.isNotEmpty()) TextButton(onClick = { vm.clearConversation() }, enabled = !vm.busy) { Text("New chat") }
                    }
                    Text("Connect a model, then start with a coding task.", color = muted)
                    Spacer(Modifier.height(16.dp))
                    vm.pendingContextName?.let { attached ->
                        Surface(color = panel, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Description, null, tint = accent)
                                Spacer(Modifier.width(8.dp))
                                Text("Attached: $attached", modifier = Modifier.weight(1f), maxLines = 1)
                                TextButton(onClick = { vm.removePendingContext() }) { Text("Remove") }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 12.dp)) {
                        if (vm.messages.isEmpty()) item {
                            Surface(color = panel, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(18.dp)) {
                                    Text("Start with a task", color = accent, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(8.dp))
                                    Text("Explain a bug, ask for an implementation plan, or attach a workspace file for focused help.", color = muted)
                                }
                            }
                        }
                        items(vm.messages) { msg -> MessageBubble(msg) }
                        if (vm.busy) item { Text("Thinking…", color = accent, modifier = Modifier.padding(8.dp)) }
                    }
                    OutlinedTextField(value = vm.input, onValueChange = { vm.input = it }, modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Ask it to build, debug, or explain…") }, minLines = 2, maxLines = 5,
                        shape = RoundedCornerShape(18.dp))
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { vm.send() }, enabled = vm.input.isNotBlank() && !vm.busy,
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Default.Send, null); Spacer(Modifier.width(8.dp)); Text(if (vm.busy) "Working…" else "Send")
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkspaceScreen(vm: ChatViewModel, onPickFolder: () -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Chat") }
            Spacer(Modifier.weight(1f))
            Text("Workspace", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onPickFolder, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(8.dp))
            Text(if (vm.workspaceUri == null) "Choose project folder" else "Change folder")
            }
            OutlinedButton(onClick = { vm.refreshWorkspace() }, enabled = vm.workspaceUri != null) { Text("Refresh") }
        }
        vm.workspaceName?.let { Text(it, color = muted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(vertical = 8.dp)) }
        if (vm.workspaceUri == null) {
            Spacer(Modifier.height(24.dp))
            Text("Choose a folder using Android's document picker. AirFite only accesses files inside the folder you grant.", color = muted)
            return
        }
        OutlinedTextField(vm.workspaceSearch, { vm.workspaceSearch = it }, modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Search, null) }, label = { Text("Filter files") }, singleLine = true)
        Spacer(Modifier.height(8.dp))
        val filtered = vm.workspaceFiles.filter { it.relativePath.contains(vm.workspaceSearch, true) }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(filtered) { entry ->
                ListItem(
                    headlineContent = { Text(entry.relativePath, maxLines = 2) },
                    supportingContent = { Text(if (entry.isDirectory) "Folder" else "${entry.size} bytes") },
                    leadingContent = { Icon(if (entry.isDirectory) Icons.Default.FolderOpen else Icons.Default.Description, null) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ListItemDefaults.colors(containerColor = panel)
                )
                if (!entry.isDirectory) {
                    TextButton(onClick = { vm.openWorkspaceFile(entry) }, modifier = Modifier.fillMaxWidth()) { Text("Preview file") }
                }
            }
        }
        vm.workspaceError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        vm.selectedFileName?.let { name ->
            Text("Preview: $name", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
            Surface(color = panel, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                Text(vm.selectedFileContent.orEmpty(), modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { if (vm.attachSelectedFileToNextMessage()) onBack() }, modifier = Modifier.fillMaxWidth()) {
                Text("Attach to next chat message")
            }
            Text("Only this selected file is sent, and only after you attach it. Context limit: 64 KB.", color = muted, style = MaterialTheme.typography.labelSmall)
        }
        Text("Read-only tools: list_files · read_file · search_files", color = muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun MessageBubble(msg: ChatMessage) {
    val isUser = msg.role == "user"
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
        Surface(color = if (isUser) Color(0xFF26372B) else panel, shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth(0.92f)) {
            Column(Modifier.padding(14.dp)) {
                Text(if (isUser) "YOU" else "AIRFITE", color = if (isUser) accent else muted,
                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp)); Text(msg.content)
            }
        }
    }
}

@Composable
fun SettingsScreen(vm: ChatViewModel, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TextButton(onClick = onBack) { Text("← Back") }
        Text("Provider settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(vm.baseUrl, { vm.baseUrl = it }, label = { Text("API base URL") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(vm.apiKey, { vm.apiKey = it }, label = { Text("API key") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(vm.model, { vm.model = it }, label = { Text("Model ID") }, modifier = Modifier.fillMaxWidth())
        if (vm.availableModels.isNotEmpty()) {
            var modelMenuOpen by remember { mutableStateOf(false) }
            Box(Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { modelMenuOpen = true }, modifier = Modifier.fillMaxWidth()) { Text("Choose from ${vm.availableModels.size} discovered models") }
                DropdownMenu(expanded = modelMenuOpen, onDismissRequest = { modelMenuOpen = false }, modifier = Modifier.heightIn(max = 320.dp)) {
                    vm.availableModels.forEach { modelId ->
                        DropdownMenuItem(text = { Text(modelId) }, onClick = { vm.model = modelId; modelMenuOpen = false })
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("OpenAI-compatible endpoint. Your key is encrypted with Android Keystore. Leave the key blank to keep the currently saved key.", color = muted)
        vm.settingsError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        vm.connectionStatus?.let { Text(it, color = if (it.startsWith("Connected")) accent else MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.testConnection() }, enabled = !vm.testingConnection, modifier = Modifier.weight(1f)) { Text(if (vm.testingConnection) "Testing…" else "Test connection") }
            OutlinedButton(onClick = { vm.fetchModels() }, enabled = !vm.loadingModels, modifier = Modifier.weight(1f)) { Text(if (vm.loadingModels) "Loading…" else "Fetch models") }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { if (vm.saveSettings()) onBack() }, modifier = Modifier.fillMaxWidth()) { Text("Save settings") }
    }
}
