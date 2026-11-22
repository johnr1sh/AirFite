package dev.johnrish.pocketengineer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { PocketEngineerApp() } }
}
private val ink = Color(0xFF101318)
private val panel = Color(0xFF191D24)
private val muted = Color(0xFF9AA4B2)
private val accent = Color(0xFF9BDB8A)

@Composable
fun PocketEngineerApp(vm: ChatViewModel = viewModel()) {
    var showSettings by remember { mutableStateOf(false) }
    MaterialTheme(colorScheme = darkColorScheme(background = ink, surface = panel, primary = accent, onBackground = Color(0xFFE9EDF2), onSurface = Color(0xFFE9EDF2))) {
        Surface(Modifier.fillMaxSize(), color = ink) {
            if (showSettings) SettingsScreen(vm) { showSettings = false } else Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Code, null, tint = accent); Spacer(Modifier.width(8.dp))
                        Column { Text("Pocket Engineer", fontWeight = FontWeight.Bold); Text("Mobile coding agent · MVP", color = muted, style = MaterialTheme.typography.labelSmall) }
                    }
                    IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, "Settings") }
                }
                Spacer(Modifier.height(20.dp)); Text("What are we building?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Connect a model, then start with a coding task.", color = muted); Spacer(Modifier.height(16.dp))
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                    items(vm.messages) { MessageBubble(it) }
                    if (vm.busy) item { Text("Thinking…", color = accent, modifier = Modifier.padding(8.dp)) }
                }
                OutlinedTextField(value = vm.input, onValueChange = { vm.input = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Ask it to build, debug, or explain…") }, minLines = 2, maxLines = 5, shape = RoundedCornerShape(18.dp))
                Spacer(Modifier.height(8.dp))
                Button(onClick = vm::send, enabled = vm.input.isNotBlank() && !vm.busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.Send, null); Spacer(Modifier.width(8.dp)); Text(if (vm.busy) "Working…" else "Send") }
            }
        }
    }
}
@Composable
private fun MessageBubble(msg: ChatMessage) {
    val isUser = msg.role == "user"
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
        Surface(color = if (isUser) Color(0xFF26372B) else panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth(0.92f)) {
            Column(Modifier.padding(14.dp)) {
                Text(if (isUser) "YOU" else "ENGINEER", color = if (isUser) accent else muted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp)); Text(msg.content)
            }
        }
    }
}
@Composable
fun SettingsScreen(vm: ChatViewModel, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TextButton(onClick = onBack) { Text("← Back") }
        Text("Provider settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Spacer(Modifier.height(12.dp))
        OutlinedTextField(vm.baseUrl, { vm.baseUrl = it }, label = { Text("API base URL") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(vm.apiKey, { vm.apiKey = it }, label = { Text("API key") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(vm.model, { vm.model = it }, label = { Text("Model ID") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp)); Text("Compatible with many OpenAI-style APIs. Keep keys private and never share screenshots containing them.", color = muted); Spacer(Modifier.height(16.dp))
        Button(onClick = { vm.saveSettings(); onBack() }, modifier = Modifier.fillMaxWidth()) { Text("Save settings") }
    }
}
