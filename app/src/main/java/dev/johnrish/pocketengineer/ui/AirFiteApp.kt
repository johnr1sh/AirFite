package dev.johnrish.pocketengineer.ui

import android.app.Application
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.johnrish.pocketengineer.ChatViewModel
import dev.johnrish.pocketengineer.ui.agents.AgentsScreen
import dev.johnrish.pocketengineer.ui.chat.ChatScreen
import dev.johnrish.pocketengineer.ui.components.AirFiteWordmark
import dev.johnrish.pocketengineer.ui.components.StatusDot
import dev.johnrish.pocketengineer.ui.settings.SettingsScreen
import dev.johnrish.pocketengineer.ui.workspace.WorkspaceScreen

private enum class Destination(val label: String, val outlined: ImageVector, val filled: ImageVector) {
    Chat("Chat", Icons.Outlined.ChatBubbleOutline, Icons.Filled.ChatBubble),
    Files("Files", Icons.Outlined.Folder, Icons.Filled.Folder),
    Agents("Agents", Icons.Outlined.Hub, Icons.Filled.Hub),
    Settings("Settings", Icons.Outlined.Settings, Icons.Filled.Settings)
}

@Composable
fun AirFiteApp(vm: ChatViewModel = viewModel()) {
    var destination by rememberSaveable { mutableStateOf(Destination.Chat) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                vm.getApplication<Application>().contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            vm.setWorkspace(uri)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { item ->
                    val selected = item == destination
                    NavigationBarItem(
                        selected = selected,
                        onClick = { destination = item },
                        icon = { Icon(if (selected) item.filled else item.outlined, contentDescription = item.label) },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner).consumeWindowInsets(inner)) {
            TopStrip(vm, onProviderClick = { destination = Destination.Settings })
            when (destination) {
                Destination.Chat -> ChatScreen(
                    vm, onOpenSettings = { destination = Destination.Settings },
                    onOpenFiles = { destination = Destination.Files }, modifier = Modifier.weight(1f)
                )
                Destination.Files -> WorkspaceScreen(
                    vm, onPickFolder = { folderPicker.launch(null) },
                    onAttached = { destination = Destination.Chat }, modifier = Modifier.weight(1f)
                )
                Destination.Agents -> AgentsScreen(vm, modifier = Modifier.weight(1f))
                Destination.Settings -> SettingsScreen(vm, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Brand on the left; real provider state on the right (a key is set or it is not). */
@Composable
private fun TopStrip(vm: ChatViewModel, onProviderClick: () -> Unit) {
    val ready = vm.apiKey.isNotBlank()
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        AirFiteWordmark(Modifier.weight(1f))
        Row(
            Modifier.clip(MaterialTheme.shapes.small).clickable(onClick = onProviderClick).padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusDot(if (ready) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline)
            Spacer(Modifier.width(8.dp))
            Text(
                if (ready) vm.model else "No provider", style = MaterialTheme.typography.labelMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 150.dp)
            )
        }
    }
}
