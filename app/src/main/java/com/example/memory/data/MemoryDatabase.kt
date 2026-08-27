package com.example.memory.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ListEntity::class, ItemEntity::class], version = 1, exportSchema = false)
abstract class MemoryDatabase : RoomDatabase() {
    abstract fun listDao(): ListDao
    abstract fun itemDao(): ItemDao
}
