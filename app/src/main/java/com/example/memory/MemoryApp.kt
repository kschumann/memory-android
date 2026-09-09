package com.example.memory

import android.app.Application
import android.util.Log
import androidx.room.Room
import com.example.memory.backup.BackupManager
import com.example.memory.data.MIGRATION_1_2
import com.example.memory.data.MIGRATION_2_3
import com.example.memory.data.MIGRATION_3_4
import com.example.memory.data.MemoryDatabase
import com.example.memory.data.MemoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

// Coalesces rapid edits into one write instead of one per keystroke, while staying well inside
// what a user would consider "effectively always current" (R2.6). MainActivity forces an
// immediate write on onPause/onStop so a pending debounced write isn't lost if the process is
// killed while backgrounded.
private const val AUTO_BACKUP_DEBOUNCE_MS = 3000L

@OptIn(FlowPreview::class)
class MemoryApp : Application() {
    lateinit var repository: MemoryRepository
        private set
    lateinit var backupManager: BackupManager
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        val database = Room.databaseBuilder(this, MemoryDatabase::class.java, "memory.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()
        repository = MemoryRepository(database.listDao(), database.itemDao(), database)
        backupManager = BackupManager(this, repository)

        repository.observeAllListsWithItems()
            .debounce(AUTO_BACKUP_DEBOUNCE_MS)
            .onEach { autoBackup() }
            .launchIn(applicationScope)
    }

    // Called from MainActivity.onPause/onStop to flush a pending debounced write before the
    // process can be killed while backgrounded (R2.6).
    fun triggerImmediateBackup() {
        applicationScope.launch { autoBackup() }
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
