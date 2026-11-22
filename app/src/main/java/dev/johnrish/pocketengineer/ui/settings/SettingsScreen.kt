package dev.johnrish.pocketengineer.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.johnrish.pocketengineer.ChatViewModel
import dev.johnrish.pocketengineer.ProviderEndpoint
import dev.johnrish.pocketengineer.ui.components.InlineNotice
import dev.johnrish.pocketengineer.ui.components.KeyValueRow
import dev.johnrish.pocketengineer.ui.components.ScreenHeader
import dev.johnrish.pocketengineer.ui.components.SectionLabel

@Composable
fun SettingsScreen(vm: ChatViewModel, modifier: Modifier = Modifier) {
    var showKey by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var connectionDialog by remember { mutableStateOf(false) }
    var wizardOpen by rememberSaveable { mutableStateOf(false) }
    var wizardStep by rememberSaveable { mutableIntStateOf(0) }
    var wizardError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "unknown"
    }

    if (wizardOpen) {
        AlertDialog(
            onDismissRequest = { wizardOpen = false },
            title = { Text(if (wizardStep < 3) "Provider setup · Step ${wizardStep + 1} of 3" else "Testing your provider") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (wizardStep == 0) {
                        Text("Where should AirFite send API requests?")
                        OutlinedTextField(value = vm.baseUrl, onValueChange = { vm.baseUrl = it; wizardError = null }, label = { Text("HTTPS endpoint") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Text("Use your provider's OpenAI-compatible API base URL.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else if (wizardStep == 1) {
                        Text("Add the API key for this provider.")
                        OutlinedTextField(value = vm.apiKey, onValueChange = { vm.apiKey = it; wizardError = null }, label = { Text("API key") }, singleLine = true, visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = { IconButton(onClick = { showKey = !showKey }) { Icon(if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = if (showKey) "Hide key" else "Show key") } }, modifier = Modifier.fillMaxWidth())
                        Text("Stored encrypted using Android Keystore.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else if (wizardStep == 2) {
                        Text("Which model should AirFite use?")
                        OutlinedTextField(value = vm.model, onValueChange = { vm.model = it; wizardError = null }, label = { Text("Model ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        if (vm.availableModels.isNotEmpty()) {
                            TextButton(onClick = { vm.model = vm.availableModels.first() }) { Text("Use first discovered model") }
                        }
                    } else {
                        if (vm.testingConnection) CircularProgressIndicator()
                        Text(vm.connectionStatus ?: "Checking provider connection…")
                    }
                    wizardError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                if (wizardStep < 2) Button(onClick = {
                    when (wizardStep) {
                        0 -> if (ProviderEndpoint.normalize(vm.baseUrl) != null) { wizardStep = 1; wizardError = null } else wizardError = ProviderEndpoint.ERROR_MESSAGE
                        1 -> if (vm.apiKey.isNotBlank()) { wizardStep = 2; wizardError = null } else wizardError = "Enter an API key to continue."
                    }
                }) { Text("Continue") }
                else if (wizardStep == 2) Button(onClick = {
                    if (vm.saveSettings()) {
                        wizardError = null
                        wizardStep = 3
                        vm.testConnection()
                    } else wizardError = "Review the provider settings and try again."
                }) { Text("Save & test") }
                else Button(onClick = { wizardOpen = false }) { Text("Done") }
            },
            dismissButton = {
                TextButton(onClick = { if (wizardStep > 0 && wizardStep < 3) wizardStep-- else wizardOpen = false }) {
                    Text(if (wizardStep in 1..2) "Back" else "Cancel")
                }
            }
        )
    }

    if (connectionDialog) {
        AlertDialog(
            onDismissRequest = { connectionDialog = false },
            title = { Text(if (vm.testingConnection) "Checking provider" else if (vm.loadingModels) "Fetching models" else "Provider status") },
            text = { Text(vm.connectionStatus ?: "Request started. Waiting for provider response…") },
            confirmButton = { Button(onClick = { connectionDialog = false }) { Text("Done") } }
        )
    }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).imePadding()) {
        ScreenHeader(title = "Settings", subtitle = "Your connection, workspace, and privacy")

        ProviderSetupGuide(vm, onStart = { wizardStep = 0; wizardOpen = true })

        SectionLabel("Connection details")
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = vm.baseUrl, onValueChange = { vm.baseUrl = it; saved = false },
                    label = { Text("API base URL") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )
                OutlinedTextField(
                    value = vm.apiKey, onValueChange = { vm.apiKey = it; saved = false },
                    label = { Text("API key") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = if (showKey) "Hide API key" else "Show API key")
                        }
                    }
                )
                OutlinedTextField(
                    value = vm.model, onValueChange = { vm.model = it; saved = false },
                    label = { Text("Model ID") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                if (vm.availableModels.isNotEmpty()) {
                    var menuOpen by remember { mutableStateOf(false) }
                    Box(Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { menuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Select discovered model · ${vm.availableModels.size}")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, modifier = Modifier.heightIn(max = 320.dp)) {
                            vm.availableModels.forEach { id ->
                                DropdownMenuItem(text = { Text(id) }, onClick = { vm.model = id; menuOpen = false; saved = false })
                            }
                        }
                    }
                }
                Text("Use an HTTPS endpoint compatible with the OpenAI chat-completions API. Your key is stored encrypted on this device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                vm.settingsError?.let { InlineNotice(text = it, isError = true) }
                vm.connectionStatus?.let { status ->
                    val good = status.startsWith("Connected") || status.startsWith("Loaded")
                    val pending = status.endsWith("…")
                    InlineNotice(text = status, isError = !good && !pending)
                }
                if (saved && vm.settingsError == null) InlineNotice(text = "Settings saved.", isError = false)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.testConnection(); connectionDialog = true }, enabled = !vm.testingConnection, modifier = Modifier.weight(1f)) {
                        Text(if (vm.testingConnection) "Testing…" else "Test")
                    }
                    OutlinedButton(onClick = { vm.fetchModels(); connectionDialog = true }, enabled = !vm.loadingModels, modifier = Modifier.weight(1f)) {
                        Text(if (vm.loadingModels) "Loading…" else "Fetch models")
                    }
                }
                Button(onClick = { saved = vm.saveSettings() }, modifier = Modifier.fillMaxWidth()) { Text("Save provider settings") }
            }
        }

        SectionLabel("Privacy & security")
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                KeyValueRow("API key storage", "Android Keystore · AES-GCM")
                KeyValueRow("Cleartext traffic", "Blocked")
                KeyValueRow("Workspace access", "Granted folder only")
                KeyValueRow("File context", "Explicit · 64 KB limit")
            }
        }

        SectionLabel("About AirFite")
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                KeyValueRow("Version", version)
                KeyValueRow("Appearance", "Dark")
                KeyValueRow("License", "Proprietary")
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ProviderSetupGuide(vm: ChatViewModel, onStart: () -> Unit) {
    val urlReady = vm.baseUrl.startsWith("https://")
    val keyReady = vm.apiKey.isNotBlank()
    val modelReady = vm.model.isNotBlank()
    val completed = listOf(urlReady, keyReady, modelReady).count { it }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Provider setup", style = MaterialTheme.typography.titleMedium)
                    Text("$completed of 3 steps complete", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
                    Text(if (completed == 3) "Ready" else "Setup", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            ProviderStep(1, "Endpoint URL", "HTTPS API endpoint", urlReady)
            ProviderStep(2, "API key", "Secret key from your provider", keyReady)
            ProviderStep(3, "Model ID", "Model identifier supported by that endpoint", modelReady)
            Text(if (completed == 3) "Configuration is ready to test." else "Complete the guided setup to connect your provider.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text(if (completed == 3) "Review & test provider" else "Start guided setup") }
        }
    }
}

@Composable
private fun ProviderStep(number: Int, title: String, detail: String, complete: Boolean) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Surface(
            shape = androidx.compose.foundation.shape.CircleShape,
            color = if (complete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            modifier = Modifier.size(28.dp)
        ) {
            androidx.compose.foundation.layout.Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text(if (complete) "✓" else number.toString(), style = MaterialTheme.typography.labelMedium, color = if (complete) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
