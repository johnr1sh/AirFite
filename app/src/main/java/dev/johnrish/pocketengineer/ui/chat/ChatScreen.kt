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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowRight
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.johnrish.pocketengineer.ChatMessage
import dev.johnrish.pocketengineer.ChatViewModel
import dev.johnrish.pocketengineer.ui.components.AirFiteMark
import dev.johnrish.pocketengineer.ui.components.InlineNotice
import dev.johnrish.pocketengineer.ui.components.ScreenHeader

private data class Starter(val label: String, val description: String, val prompt: String, val icon: ImageVector)

private val starters = listOf(
    Starter("Explain this code", "Understand logic and spot risky patterns", "Explain what this code does and point out anything risky.", Icons.Outlined.Code),
    Starter("Find the bug", "Trace an issue and suggest a fix", "Help me find the bug in this code. Tell me what else you need to see.", Icons.Outlined.Search),
    Starter("Plan a change", "Break a feature into safe steps", "Outline a step-by-step plan for making this change safely.", Icons.Outlined.Description)
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
        ScreenHeader(title = "Chat", subtitle = vm.workspaceName ?: "Ready for your next idea") {
            if (vm.messages.isNotEmpty()) {
                TextButton(onClick = { vm.clearConversation() }, enabled = !vm.busy) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("New chat")
                }
            }
        }
        if (vm.apiKey.isBlank()) {
            InlineNotice(
                text = "Connect an AI provider to start chatting.",
                isError = false,
                action = { TextButton(onClick = onOpenSettings) { Text("Set up") } }
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 20.dp)
        ) {
            if (vm.messages.isEmpty()) item { EmptyChat(onPick = { vm.input = it }) }
            items(vm.messages) { message -> MessageRow(message) }
            if (vm.busy) item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("AirFite is thinking…", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        vm.pendingContextName?.let { attached ->
            AssistChip(
                onClick = { vm.removePendingContext() },
                label = { Text(attached, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Filled.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp)) },
                trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Remove attachment", modifier = Modifier.size(16.dp)) }
            )
        }
        Surface(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.Bottom) {
                IconButton(onClick = onOpenFiles, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Filled.AttachFile, contentDescription = "Attach workspace file")
                }
                OutlinedTextField(
                    value = vm.input,
                    onValueChange = { vm.input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask AirFite anything…") },
                    minLines = 1,
                    maxLines = 5,
                    shape = RoundedCornerShape(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = { vm.send() },
                    enabled = vm.input.isNotBlank() && !vm.busy,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send message")
                }
            }
        }
    }
}

@Composable
private fun EmptyChat(onPick: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(84.dp)
        ) {
            Box(contentAlignment = Alignment.Center) { AirFiteMark(markSize = 48.dp) }
        }
        Spacer(Modifier.height(20.dp))
        Text("What are we building?", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Your coding partner for ideas, debugging, and building.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("QUICK START", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text("Tap to add a prompt", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        starters.forEach { starter ->
            Surface(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).clickable { onPick(starter.prompt) },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(42.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(starter.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(21.dp))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(starter.label, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(3.dp))
                        Text(starter.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp).clickable { onPick("Help me work with a file in my workspace.") },
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AttachFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text("Attach a workspace file for context", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Box(Modifier.width(3.dp).fillMaxHeight().background(if (isUser) scheme.outline else scheme.primary))
            Spacer(Modifier.width(12.dp))
            SelectionContainer { Text(message.content, style = MaterialTheme.typography.bodyLarge) }
        }
    }
}
