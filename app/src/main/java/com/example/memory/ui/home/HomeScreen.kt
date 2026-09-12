package com.example.memory.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.memory.MemoryApp
import com.example.memory.R
import com.example.memory.backup.BackupHealth
import com.example.memory.backup.RestorePreflight
import com.example.memory.common.AppIcon
import com.example.memory.common.ScreenTopBar
import com.example.memory.common.SwipeActionBox
import com.example.memory.data.ListEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenList: (Long) -> Unit) {
    val application = LocalContext.current.applicationContext as MemoryApp
    val viewModel: HomeViewModel = viewModel(
        factory = viewModelFactory { initializer { HomeViewModel(application.repository) } }
    )
    val backupManager = remember { application.backupManager }
    val scope = rememberCoroutineScope()

    val lists by viewModel.lists.collectAsStateWithLifecycle()
    val editingId by viewModel.editingId.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val flatItems by viewModel.flatItems.collectAsStateWithLifecycle()
    val backupHealth by backupManager.health.collectAsStateWithLifecycle()

    var localLists by remember { mutableStateOf(lists) }
    LaunchedEffect(lists) { localLists = lists }

    var localFlatItems by remember { mutableStateOf(flatItems) }
    LaunchedEffect(flatItems) { localFlatItems = flatItems }

    var settingsExpanded by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.undoEvents.collect { message ->
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.onUndoDelete()
        }
    }
    val folderPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            backupManager.saveFolderUri(uri)
            scope.launch {
                try {
                    backupManager.exportNow(uri)
                } catch (e: Exception) {
                    // exportNow already recorded this as unhealthy; the banner below covers it.
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        if (backupManager.getSavedFolderUri() == null) {
            folderPickerLauncher.launch(null)
        }
    }

    // R4: restore-from-file. Picking a file only validates it (R4.3/R4.4) - nothing is written
    // until the user reviews the preflight summary and explicitly confirms (R4.5).
    var restorePreflight by remember { mutableStateOf<RestorePreflight?>(null) }
    var showNoDestinationDialog by remember { mutableStateOf(false) }
    var restoring by remember { mutableStateOf(false) }
    val restoreFilePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch { restorePreflight = backupManager.validatePickedFile(uri) }
        }
    }

    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        localLists = localLists.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }

    val flatListState = rememberLazyListState()
    val flatReorderState = rememberReorderableLazyListState(flatListState) { from, to ->
        localFlatItems = localFlatItems.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }

    Scaffold(
        topBar = {
            ScreenTopBar {
                TopAppBar(
                    expandedHeight = 128.dp,
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(size = 60.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("My Memory")
                        }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { settingsExpanded = true }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_settings),
                                    contentDescription = "Settings"
                                )
                            }
                            DropdownMenu(
                                expanded = settingsExpanded,
                                onDismissRequest = { settingsExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(if (viewMode == HomeViewMode.CARDS) "List View" else "Card View") },
                                    onClick = {
                                        viewModel.onToggleViewMode()
                                        settingsExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Restore from file…") },
                                    onClick = {
                                        settingsExpanded = false
                                        // A backup destination is required first: the undo
                                        // snapshot (R4.6) is restore's only safety net, so restore
                                        // never proceeds without somewhere to write it.
                                        if (backupManager.getSavedFolderUri() == null) {
                                            showNoDestinationDialog = true
                                        } else {
                                            restoreFilePickerLauncher.launch(arrayOf("*/*"))
                                        }
                                    }
                                )
                            }
                        }
                    }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (viewMode == HomeViewMode.CARDS) {
                FloatingActionButton(onClick = { viewModel.onAddClicked() }) {
                    Icon(Icons.Filled.Add, contentDescription = "New list")
                }
            }
        }
    ) { innerPadding ->
        val focusManager = LocalFocusManager.current
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
        if (backupHealth == BackupHealth.UNHEALTHY) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { folderPickerLauncher.launch(null) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Backup isn't working - tap to choose a new folder",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }
        ) {
        if (viewMode == HomeViewMode.CARDS) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(localLists, key = { it.id }) { list ->
                    ReorderableItem(reorderState, key = list.id) { _ ->
                        SwipeActionBox(
                            key = list.id,
                            onDelete = { viewModel.onDeleteRequested(list) },
                            confirmMessage = "Are you sure you want to delete \"${list.name.ifBlank { "Untitled" }}\"?"
                        ) {
                            ListCard(
                                list = list,
                                isEditing = editingId == list.id,
                                onOpen = { onOpenList(list.id) },
                                onStartRename = { viewModel.onStartRename(list.id) },
                                onCommitEdit = { newText -> viewModel.onCommitEdit(list, newText) },
                                dragHandleModifier = Modifier.draggableHandle(
                                    onDragStopped = { viewModel.onReorder(localLists) }
                                )
                            )
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                state = flatListState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(localFlatItems, key = { it.item.id }) { entry ->
                    ReorderableItem(flatReorderState, key = entry.item.id) { _ ->
                        FlatItemRow(
                            entry = entry,
                            dragHandleModifier = Modifier.draggableHandle(
                                onDragStopped = { viewModel.onReorderFlatItems(localFlatItems) }
                            )
                        )
                    }
                }
            }
        }
        }
        }
    }

    if (showNoDestinationDialog) {
        AlertDialog(
            onDismissRequest = { showNoDestinationDialog = false },
            title = { Text("Choose a backup folder first") },
            text = {
                Text(
                    "Restoring writes a safety copy of your current data to your backup folder " +
                        "before replacing it, so a folder needs to be set up first."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showNoDestinationDialog = false
                    folderPickerLauncher.launch(null)
                }) { Text("Choose folder") }
            },
            dismissButton = {
                TextButton(onClick = { showNoDestinationDialog = false }) { Text("Cancel") }
            }
        )
    }

    when (val preflight = restorePreflight) {
        is RestorePreflight.Rejected -> {
            AlertDialog(
                onDismissRequest = { restorePreflight = null },
                title = { Text("Can't restore this file") },
                text = { Text(preflight.reason) },
                confirmButton = {
                    TextButton(onClick = { restorePreflight = null }) { Text("OK") }
                }
            )
        }
        is RestorePreflight.Ready -> {
            val dateFormat = remember { SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault()) }
            AlertDialog(
                onDismissRequest = { if (!restoring) restorePreflight = null },
                title = { Text("Replace all local data?") },
                text = {
                    Column {
                        Text("This file:")
                        Text("• Format version ${preflight.export.formatVersion}")
                        Text("• Exported ${dateFormat.format(Date(preflight.export.exportedAt))}")
                        if (preflight.export.appVersion.isNotBlank()) {
                            Text("• From app version ${preflight.export.appVersion}")
                        }
                        Text("• ${preflight.fileListCount} lists, ${preflight.fileItemCount} notes")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("You currently have ${preflight.currentListCount} lists, ${preflight.currentItemCount} notes.")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Your current data will be replaced. A safety copy is saved to your backup folder first.")
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = !restoring,
                        onClick = {
                            restoring = true
                            scope.launch {
                                try {
                                    backupManager.performRestore(preflight.export)
                                    snackbarHostState.showSnackbar("Restore complete")
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar("Restore failed: ${e.message}")
                                } finally {
                                    restoring = false
                                    restorePreflight = null
                                }
                            }
                        }
                    ) { Text(if (restoring) "Restoring…" else "Replace data") }
                },
                dismissButton = {
                    TextButton(enabled = !restoring, onClick = { restorePreflight = null }) { Text("Cancel") }
                }
            )
        }
        null -> Unit
    }
}
