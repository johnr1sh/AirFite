package dev.johnrish.pocketengineer.ui.workspace

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.johnrish.pocketengineer.ChatViewModel
import dev.johnrish.pocketengineer.WorkspaceEntry
import dev.johnrish.pocketengineer.WorkspaceTools
import dev.johnrish.pocketengineer.ui.components.EmptyState
import dev.johnrish.pocketengineer.ui.components.InlineNotice
import dev.johnrish.pocketengineer.ui.components.ScreenHeader
import dev.johnrish.pocketengineer.ui.theme.CodeTextStyle

private val codeExtensions = setOf(
    "kt", "kts", "java", "js", "jsx", "ts", "tsx", "py", "go", "rs", "c", "h", "cpp", "cs", "rb", "php",
    "swift", "sh", "gradle", "json", "xml", "yml", "yaml", "toml", "html", "css", "sql"
)

private fun iconFor(entry: WorkspaceEntry): ImageVector = when {
    entry.isDirectory -> Icons.Outlined.Folder
    entry.name.substringAfterLast('.', "").lowercase() in codeExtensions -> Icons.Outlined.Code
    else -> Icons.Outlined.Description
}

private fun formatSize(bytes: Long): String = when {
    bytes < 0 -> ""
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

@Composable
fun WorkspaceScreen(
    vm: ChatViewModel,
    onPickFolder: () -> Unit,
    onAttached: () -> Unit,
    modifier: Modifier = Modifier
) {
    var previewOpen by rememberSaveable { mutableStateOf(false) }
    if (previewOpen && vm.selectedFileName != null) {
        FilePreview(
            vm = vm,
            onClose = { previewOpen = false },
            onAttached = { previewOpen = false; onAttached() },
            modifier = modifier
        )
    } else {
        FileBrowser(vm, onPickFolder, onOpenPreview = { previewOpen = true }, modifier = modifier)
    }
}

@Composable
private fun FileBrowser(vm: ChatViewModel, onPickFolder: () -> Unit, onOpenPreview: () -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        ScreenHeader(title = "Files", subtitle = vm.workspaceName ?: "No workspace selected") {
            if (vm.workspaceUri != null) {
                IconButton(onClick = { vm.refreshWorkspace() }) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh files") }
            }
            IconButton(onClick = onPickFolder) {
                Icon(Icons.Outlined.FolderOpen, contentDescription = if (vm.workspaceUri == null) "Choose folder" else "Change folder")
            }
        }
        if (vm.workspaceUri == null) {
            EmptyState(
                icon = Icons.Outlined.FolderOpen,
                title = "No workspace yet",
                body = "Choose a project folder with Android's document picker. AirFite only reads inside the folder you grant.",
                action = { Button(onClick = onPickFolder) { Text("Choose folder") } }
            )
        } else {
            val filter = vm.workspaceSearch
            val filtered = remember(vm.workspaceFiles, filter) {
                vm.workspaceFiles.filter { it.relativePath.contains(filter, ignoreCase = true) }
            }
            OutlinedTextField(
                value = filter,
                onValueChange = { vm.workspaceSearch = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Filter by path") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (filter.isNotEmpty()) {
                        IconButton(onClick = { vm.workspaceSearch = "" }) { Icon(Icons.Filled.Close, contentDescription = "Clear filter") }
                    }
                }
            )
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp)) {\n                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {\n                    Icon(Icons.Outlined.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))\n                    Spacer(Modifier.width(8.dp))\n                    Text("${filtered.size} visible", style = MaterialTheme.typography.labelMedium)\n                    Spacer(Modifier.weight(1f))\n                    Text("${vm.workspaceFiles.size} total", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)\n                }\n            }
            vm.workspaceError?.let { InlineNotice(text = it, isError = true) }
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                items(filtered) { entry ->
                    FileRow(entry, showFullPath = filter.isNotEmpty()) {
                        vm.openWorkspaceFile(entry)
                        if (vm.selectedFileName == entry.relativePath) onOpenPreview()
                    }
                }
            }
        }
    }
}

@Composable
private fun FileRow(entry: WorkspaceEntry, showFullPath: Boolean, onClick: () -> Unit) {
    val depth = if (showFullPath) 0 else entry.relativePath.count { it == '/' }.coerceAtMost(8)
    Row(
        Modifier.fillMaxWidth().clickable(enabled = !entry.isDirectory, onClick = onClick)
            .padding(start = (depth * 14).dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            iconFor(entry), contentDescription = null, modifier = Modifier.size(20.dp),
            tint = if (entry.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(12.dp))
        Text(
            if (showFullPath) entry.relativePath else entry.name,
            Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        if (!entry.isDirectory) {
            Spacer(Modifier.width(12.dp))
            Text(formatSize(entry.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FilePreview(vm: ChatViewModel, onClose: () -> Unit, onAttached: () -> Unit, modifier: Modifier) {
    val name = vm.selectedFileName.orEmpty()
    val lines = remember(vm.selectedFileContent) { vm.selectedFileContent.orEmpty().lines() }
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to files") }
            Text(name, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            "${lines.size} lines", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
        )
        Surface(
            Modifier.weight(1f).fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.small
        ) {
            LazyColumn(Modifier.padding(vertical = 8.dp)) {
                items(lines.size) { index ->
                    Row(Modifier.padding(horizontal = 10.dp)) {
                        Text(
                            (index + 1).toString().padStart(4), style = CodeTextStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(lines[index], style = CodeTextStyle, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        vm.workspaceError?.let { InlineNotice(text = it, isError = true) }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { if (vm.attachSelectedFileToNextMessage()) onAttached() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Attach to next message")
        }
        Text(
            "Only this file is sent, and only after you attach it. Limit 64 KB; credential-looking files are blocked.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
        )
    }
}
