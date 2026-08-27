package com.example.memory

import android.app.Application
import androidx.room.Room
import com.example.memory.backup.BackupManager
import com.example.memory.data.MemoryDatabase
import com.example.memory.data.MemoryRepository

class MemoryApp : Application() {
    lateinit var repository: MemoryRepository
        private set
    lateinit var backupManager: BackupManager
        private set

    override fun onCreate() {
        super.onCreate()
        val database = Room.databaseBuilder(this, MemoryDatabase::class.java, "memory.db").build()
        repository = MemoryRepository(database.listDao(), database.itemDao())
        backupManager = BackupManager(this, repository)
    }
}
