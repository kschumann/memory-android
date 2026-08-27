package com.example.memory.ui.listdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.memory.data.ITEM_TEXT_MAX_LENGTH
import com.example.memory.data.ItemEntity
import com.example.memory.data.LIST_NAME_MAX_LENGTH
import com.example.memory.data.ListEntity
import com.example.memory.data.MemoryRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val UNDO_WINDOW_MS = 4000L

class ListDetailViewModel(
    private val listId: Long,
    private val repository: MemoryRepository
) : ViewModel() {

    val list: StateFlow<ListEntity?> =
        repository.observeList(listId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val pendingDeleteId = MutableStateFlow<Long?>(null)
    private var pendingDelete: ItemEntity? = null
    private var pendingDeleteJob: Job? = null

    val items: StateFlow<List<ItemEntity>> =
        combine(repository.observeItems(listId), pendingDeleteId) { all, deletedId ->
            all.filter { it.id != deletedId }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _editingId = MutableStateFlow<Long?>(null)
    val editingId: StateFlow<Long?> = _editingId

    private val _titleEditing = MutableStateFlow(false)
    val titleEditing: StateFlow<Boolean> = _titleEditing

    private val _undoEvents = Channel<String>(Channel.BUFFERED)
    val undoEvents = _undoEvents.receiveAsFlow()

    fun onAddClicked() {
        viewModelScope.launch {
            val id = repository.insertItemAtTop(listId, "")
            _editingId.value = id
        }
    }

    fun onStartEdit(id: Long) {
        _editingId.value = id
    }

    fun onCommitEdit(item: ItemEntity, newText: String) {
        viewModelScope.launch {
            val trimmed = newText.trim().take(ITEM_TEXT_MAX_LENGTH)
            if (trimmed.isEmpty()) {
                repository.deleteItem(item)
            } else if (trimmed != item.text) {
                repository.editItem(item, trimmed)
            }
            if (_editingId.value == item.id) _editingId.value = null
        }
    }

    fun onStartRenameTitle() {
        _titleEditing.value = true
    }

    fun onCommitTitleEdit(list: ListEntity, newName: String) {
        onRenameList(list, newName)
        _titleEditing.value = false
    }

    fun onRenameList(list: ListEntity, newName: String) {
        viewModelScope.launch {
            val trimmed = newName.trim().take(LIST_NAME_MAX_LENGTH)
            if (trimmed.isNotEmpty() && trimmed != list.name) {
                repository.renameList(list, trimmed)
            }
        }
    }

    fun onReorder(newOrder: List<ItemEntity>) {
        viewModelScope.launch { repository.reorderItems(newOrder) }
    }

    fun onDeleteRequested(item: ItemEntity) {
        val previous = pendingDelete
        if (previous != null) {
            pendingDeleteJob?.cancel()
            viewModelScope.launch { repository.deleteItem(previous) }
        }
        pendingDelete = item
        pendingDeleteId.value = item.id
        pendingDeleteJob = viewModelScope.launch {
            delay(UNDO_WINDOW_MS)
            repository.deleteItem(item)
            pendingDelete = null
            pendingDeleteId.value = null
        }
        _undoEvents.trySend("Note deleted")
    }

    fun onUndoDelete() {
        pendingDeleteJob?.cancel()
        pendingDelete = null
        pendingDeleteId.value = null
    }
}
