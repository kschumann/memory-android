package com.example.memory.ui.listdetail

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.memory.MemoryApp
import com.example.memory.common.ScreenTopBar
import com.example.memory.common.SwipeToDeleteBox
import com.example.memory.data.LIST_NAME_MAX_LENGTH
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListDetailScreen(listId: Long, onBack: () -> Unit) {
    val application = LocalContext.current.applicationContext as MemoryApp
    val viewModel: ListDetailViewModel = viewModel(
        factory = viewModelFactory { initializer { ListDetailViewModel(listId, application.repository) } }
    )

    val list by viewModel.list.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val editingId by viewModel.editingId.collectAsStateWithLifecycle()
    val titleEditing by viewModel.titleEditing.collectAsStateWithLifecycle()

    var localItems by remember { mutableStateOf(items) }
    LaunchedEffect(items) { localItems = items }

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

    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        localItems = localItems.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }

    Scaffold(
        topBar = {
          ScreenTopBar {
            CenterAlignedTopAppBar(
                title = {
                    val currentList = list
                    if (currentList == null) {
                        Text("")
                    } else if (titleEditing) {
                        var fieldValue by remember(currentList.id) {
                            mutableStateOf(TextFieldValue(currentList.name, selection = TextRange(currentList.name.length)))
                        }
                        var hasFocusedOnce by remember(currentList.id) { mutableStateOf(false) }
                        val focusRequester = remember { FocusRequester() }
                        val keyboardController = LocalSoftwareKeyboardController.current
                        LaunchedEffect(Unit) { focusRequester.requestFocus() }
                        TextField(
                            value = fieldValue,
                            onValueChange = { if (it.text.length <= LIST_NAME_MAX_LENGTH) fieldValue = it },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
                            colors = TextFieldDefaults.colors(),
                            modifier = Modifier
                                .focusRequester(focusRequester)
                                .onFocusChanged { state ->
                                    if (state.isFocused) {
                                        hasFocusedOnce = true
                                    } else if (hasFocusedOnce) {
                                        viewModel.onCommitTitleEdit(currentList, fieldValue.text)
                                    }
                                }
                        )
                    } else {
                        Text(
                            text = currentList.name.ifBlank { "Untitled" },
                            modifier = Modifier.pointerInput(currentList.id) {
                                detectTapGestures(onLongPress = { viewModel.onStartRenameTitle() })
                            }
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
          }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.onAddClicked() }) {
                Icon(Icons.Filled.Add, contentDescription = "New note")
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
            items(localItems, key = { it.id }) { item ->
                ReorderableItem(reorderState, key = item.id) { _ ->
                    SwipeToDeleteBox(
                        key = item.id,
                        onDelete = { viewModel.onDeleteRequested(item) },
                        confirmMessage = "Are you sure you want to delete this memory?"
                    ) {
                        ItemCard(
                            item = item,
                            onStartEdit = { viewModel.onStartEdit(item.id) },
                            dragHandleModifier = Modifier.draggableHandle(
                                onDragStopped = { viewModel.onReorder(localItems) }
                            )
                        )
                    }
                }
            }
        }
        val editingItem = localItems.find { it.id == editingId }
        if (editingItem != null) {
            EditItemOverlay(
                item = editingItem,
                onCancel = { viewModel.onCancelEdit(editingItem) },
                onSave = { newText -> viewModel.onCommitEdit(editingItem, newText) },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
            )
        }
        }
    }
}
