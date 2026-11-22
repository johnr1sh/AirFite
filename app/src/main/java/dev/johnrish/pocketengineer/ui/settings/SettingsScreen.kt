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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.johnrish.pocketengineer.ChatViewModel
import dev.johnrish.pocketengineer.ui.components.InlineNotice
import dev.johnrish.pocketengineer.ui.components.KeyValueRow
import dev.johnrish.pocketengineer.ui.components.ScreenHeader
import dev.johnrish.pocketengineer.ui.components.SectionLabel

@Composable
fun SettingsScreen(vm: ChatViewModel, modifier: Modifier = Modifier) {
    var showKey by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "unknown"
    }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).imePadding()) {
        ScreenHeader(title = "Settings", subtitle = "Configure your AI provider and security")

        SectionLabel("AI provider")
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
                Text("OpenAI-compatible HTTPS endpoints are supported. Leave the key blank to retain the saved key.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                vm.settingsError?.let { InlineNotice(text = it, isError = true) }
                vm.connectionStatus?.let { status ->
                    val good = status.startsWith("Connected") || status.startsWith("Loaded")
                    val pending = status.endsWith("…")
                    InlineNotice(text = status, isError = !good && !pending)
                }
                if (saved && vm.settingsError == null) InlineNotice(text = "Settings saved.", isError = false)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.testConnection() }, enabled = !vm.testingConnection, modifier = Modifier.weight(1f)) {
                        Text(if (vm.testingConnection) "Testing…" else "Test")
                    }
                    OutlinedButton(onClick = { vm.fetchModels() }, enabled = !vm.loadingModels, modifier = Modifier.weight(1f)) {
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
