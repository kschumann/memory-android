package com.example.memory.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

private const val UNDO_WINDOW_MS = 5000L

class HomeViewModel(private val repository: MemoryRepository) : ViewModel() {

    private val pendingDeleteId = MutableStateFlow<Long?>(null)
    private var pendingDelete: ListEntity? = null
    private var pendingDeleteJob: Job? = null

    val lists: StateFlow<List<ListEntity>> =
        combine(repository.observeLists(), pendingDeleteId) { all, deletedId ->
            all.filter { it.id != deletedId }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _editingId = MutableStateFlow<Long?>(null)
    val editingId: StateFlow<Long?> = _editingId

    private val _undoEvents = Channel<String>(Channel.BUFFERED)
    val undoEvents = _undoEvents.receiveAsFlow()

    private val _exportEvents = Channel<String>(Channel.BUFFERED)
    val exportEvents = _exportEvents.receiveAsFlow()

    fun onExportResult(message: String) {
        _exportEvents.trySend(message)
    }

    fun onAddClicked() {
        viewModelScope.launch {
            val id = repository.insertListAtTop("")
            _editingId.value = id
        }
    }

    fun onStartRename(id: Long) {
        _editingId.value = id
    }

    fun onCommitEdit(list: ListEntity, newText: String) {
        viewModelScope.launch {
            val trimmed = newText.trim().take(LIST_NAME_MAX_LENGTH)
            if (trimmed.isEmpty()) {
                repository.deleteList(list)
            } else if (trimmed != list.name) {
                repository.renameList(list, trimmed)
            }
            if (_editingId.value == list.id) _editingId.value = null
        }
    }

    fun onReorder(newOrder: List<ListEntity>) {
        viewModelScope.launch { repository.reorderLists(newOrder) }
    }

    fun onDeleteRequested(list: ListEntity) {
        val previous = pendingDelete
        if (previous != null) {
            pendingDeleteJob?.cancel()
            viewModelScope.launch { repository.deleteList(previous) }
        }
        pendingDelete = list
        pendingDeleteId.value = list.id
        pendingDeleteJob = viewModelScope.launch {
            delay(UNDO_WINDOW_MS)
            repository.deleteList(list)
            pendingDelete = null
            pendingDeleteId.value = null
        }
        _undoEvents.trySend("\"${list.name.ifBlank { "Untitled" }}\" deleted")
    }

    fun onUndoDelete() {
        pendingDeleteJob?.cancel()
        pendingDelete = null
        pendingDeleteId.value = null
    }
}
