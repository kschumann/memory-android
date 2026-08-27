package com.example.memory.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ListDao {
    @Query("SELECT * FROM lists ORDER BY sortOrder ASC")
    fun observeLists(): Flow<List<ListEntity>>

    @Transaction
    @Query("SELECT * FROM lists ORDER BY sortOrder ASC")
    suspend fun getAllListsWithItems(): List<ListWithItems>

    @Query("SELECT * FROM lists WHERE id = :id")
    fun observeList(id: Long): Flow<ListEntity?>

    @Query("SELECT COALESCE(MIN(sortOrder), 0) FROM lists")
    suspend fun minSortOrder(): Int

    @Insert
    suspend fun insert(list: ListEntity): Long

    @Update
    suspend fun update(list: ListEntity): Unit

    @Update
    suspend fun updateAll(lists: List<ListEntity>): Unit

    @Delete
    suspend fun delete(list: ListEntity): Unit
}
