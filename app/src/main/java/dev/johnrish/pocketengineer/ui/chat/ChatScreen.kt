package dev.johnrish.pocketengineer.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.johnrish.pocketengineer.ChatMessage
import dev.johnrish.pocketengineer.ChatViewModel
import dev.johnrish.pocketengineer.ui.components.AirFiteMark
import dev.johnrish.pocketengineer.ui.components.InlineNotice
import dev.johnrish.pocketengineer.ui.components.ScreenHeader

private data class Starter(val label: String, val prompt: String, val icon: ImageVector)

private val starters = listOf(
    Starter("Explain this code", "Explain what this code does and point out anything risky.", Icons.Outlined.Code),
    Starter("Find the bug", "Help me find the bug in this code. Tell me what else you need to see.", Icons.Outlined.Search),
    Starter("Plan a change", "Outline a step-by-step plan for making this change safely.", Icons.Outlined.Description)
)

@Composable
fun ChatScreen(
    vm: ChatViewModel,
    onOpenSettings: () -> Unit,
    onOpenFiles: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val itemCount = vm.messages.size + if (vm.busy) 1 else 0
    LaunchedEffect(itemCount) {
        if (itemCount > 0) listState.animateScrollToItem(itemCount - 1)
    }

    Column(modifier.fillMaxSize().padding(horizontal = 16.dp).imePadding()) {
        ScreenHeader(title = "Chat", subtitle = vm.workspaceName ?: "No workspace selected") {
            if (vm.messages.isNotEmpty()) {
                TextButton(onClick = { vm.clearConversation() }, enabled = !vm.busy) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("New chat")
                }
            }
        }
        if (vm.apiKey.isBlank()) {
            InlineNotice(
                text = "No API key is set, so messages can't be sent yet.",
                isError = false,
                action = { TextButton(onClick = onOpenSettings) { Text("Settings") } }
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            if (vm.messages.isEmpty()) item { EmptyChat(onPick = { vm.input = it }) }
            items(vm.messages) { message -> MessageRow(message) }
            if (vm.busy) item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Waiting for the model…", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        vm.pendingContextName?.let { attached ->
            AssistChip(
                onClick = { vm.removePendingContext() },
                label = { Text(attached, maxLines = 1) },
                leadingIcon = { Icon(Icons.Filled.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp)) },
                trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Remove attachment", modifier = Modifier.size(16.dp)) }
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
            IconButton(onClick = onOpenFiles) {
                Icon(Icons.Filled.AttachFile, contentDescription = "Attach a file from the workspace")
            }
            OutlinedTextField(
                value = vm.input,
                onValueChange = { vm.input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask AirFite to build, debug, or explain") },
                minLines = 1,
                maxLines = 5
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(onClick = { vm.send() }, enabled = vm.input.isNotBlank() && !vm.busy) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}

@Composable
private fun EmptyChat(onPick: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        AirFiteMark(markSize = 52.dp)
        Spacer(Modifier.height(16.dp))
        Text("What are we building?", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            "Attach a workspace file for focused help, or start from a prompt.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        starters.forEach { starter ->
            Row(
                Modifier.fillMaxWidth().clickable { onPick(starter.prompt) }.padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(starter.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(starter.label, style = MaterialTheme.typography.bodyLarge)
                    Text(starter.prompt, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun MessageRow(message: ChatMessage) {
    val isUser = message.role == "user"
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        Text(
            if (isUser) "YOU" else "AIRFITE", style = MaterialTheme.typography.labelSmall,
            color = if (isUser) scheme.onSurfaceVariant else scheme.primary
        )
        Spacer(Modifier.height(6.dp))
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(2.dp).fillMaxHeight().background(if (isUser) scheme.outline else scheme.primary))
            Spacer(Modifier.width(12.dp))
            SelectionContainer { Text(message.content, style = MaterialTheme.typography.bodyLarge) }
        }
    }
}
