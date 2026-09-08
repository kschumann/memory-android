package com.example.memory.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Query("SELECT * FROM items WHERE listId = :listId AND archived = 0 ORDER BY sortOrder ASC")
    fun observeItems(listId: Long): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE listId = :listId AND archived = 1 ORDER BY archivedAt DESC")
    fun observeArchivedItems(listId: Long): Flow<List<ItemEntity>>

    @Query("SELECT COALESCE(MIN(sortOrder), 0) FROM items WHERE listId = :listId")
    suspend fun minSortOrder(listId: Long): Int

    @Query("SELECT COALESCE(MIN(globalSortOrder), 0) FROM items")
    suspend fun minGlobalSortOrder(): Int

    @Query(
        """
        SELECT items.*, lists.name AS listName
        FROM items
        INNER JOIN lists ON items.listId = lists.id
        WHERE items.archived = 0
        ORDER BY items.globalSortOrder ASC
        """
    )
    fun observeAllItemsWithListName(): Flow<List<ItemWithListName>>

    @Insert
    suspend fun insert(item: ItemEntity): Long

    @Update
    suspend fun update(item: ItemEntity): Unit

    @Update
    suspend fun updateAll(items: List<ItemEntity>): Unit

    @Delete
    suspend fun delete(item: ItemEntity): Unit
}
