package com.example.memory.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Query("SELECT * FROM items WHERE listId = :listId ORDER BY sortOrder ASC")
    fun observeItems(listId: Long): Flow<List<ItemEntity>>

    @Query("SELECT COALESCE(MIN(sortOrder), 0) FROM items WHERE listId = :listId")
    suspend fun minSortOrder(listId: Long): Int

    @Insert
    suspend fun insert(item: ItemEntity): Long

    @Update
    suspend fun update(item: ItemEntity): Unit

    @Update
    suspend fun updateAll(items: List<ItemEntity>): Unit

    @Delete
    suspend fun delete(item: ItemEntity): Unit
}
