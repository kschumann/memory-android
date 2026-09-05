package com.example.memory.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.memory.MemoryApp
import com.example.memory.common.AppIcon
import com.example.memory.common.ScreenTopBar
import com.example.memory.common.SwipeToDeleteBox
import com.example.memory.data.ListEntity
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

    var localLists by remember { mutableStateOf(lists) }
    LaunchedEffect(lists) { localLists = lists }

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
                    // Auto-backup will retry on the next change; nothing actionable to show here.
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        if (backupManager.getSavedFolderUri() == null) {
            folderPickerLauncher.launch(null)
        }
    }

    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        localLists = localLists.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }

    Scaffold(
        topBar = {
            ScreenTopBar {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppIcon()
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("My Memory")
                        }
                    }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.onAddClicked() }) {
                Icon(Icons.Filled.Add, contentDescription = "New list")
            }
        }
    ) { innerPadding ->
        val focusManager = LocalFocusManager.current
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }
        ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(localLists, key = { it.id }) { list ->
                ReorderableItem(reorderState, key = list.id) { _ ->
                    SwipeToDeleteBox(
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
        }
    }
}
