package com.example.memory

import android.app.Application
import android.util.Log
import androidx.room.Room
import com.example.memory.backup.BackupManager
import com.example.memory.data.MemoryDatabase
import com.example.memory.data.MemoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

private const val AUTO_BACKUP_DEBOUNCE_MS = 500L

@OptIn(FlowPreview::class)
class MemoryApp : Application() {
    lateinit var repository: MemoryRepository
        private set
    lateinit var backupManager: BackupManager
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        val database = Room.databaseBuilder(this, MemoryDatabase::class.java, "memory.db").build()
        repository = MemoryRepository(database.listDao(), database.itemDao())
        backupManager = BackupManager(this, repository)

        repository.observeAllListsWithItems()
            .debounce(AUTO_BACKUP_DEBOUNCE_MS)
            .onEach { autoBackup() }
            .launchIn(applicationScope)
    }

    private suspend fun autoBackup() {
        val folderUri = backupManager.getSavedFolderUri() ?: return
        try {
            backupManager.exportNow(folderUri)
        } catch (e: Exception) {
            Log.w("MemoryApp", "Auto backup failed", e)
        }
    }
}
