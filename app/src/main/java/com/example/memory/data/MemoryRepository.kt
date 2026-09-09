package com.example.memory.data

import androidx.room.withTransaction
import com.example.memory.backup.BackupExport
import java.util.UUID
import kotlinx.coroutines.flow.Flow

class MemoryRepository(
    private val listDao: ListDao,
    private val itemDao: ItemDao,
    private val database: MemoryDatabase
) {
    fun observeLists(): Flow<List<ListEntity>> = listDao.observeLists()

    fun observeList(id: Long): Flow<ListEntity?> = listDao.observeList(id)

    fun observeItems(listId: Long): Flow<List<ItemEntity>> = itemDao.observeItems(listId)

    fun observeArchivedItems(listId: Long): Flow<List<ItemEntity>> = itemDao.observeArchivedItems(listId)

    fun observeAllItemsFlat(): Flow<List<ItemWithListName>> = itemDao.observeAllItemsWithListName()

    suspend fun getAllListsWithItems(): List<ListWithItems> = listDao.getAllListsWithItems()

    fun observeAllListsWithItems(): Flow<List<ListWithItems>> = listDao.observeAllListsWithItems()

    suspend fun insertListAtTop(name: String): Long {
        val sortOrder = listDao.minSortOrder() - 1
        return listDao.insert(ListEntity(name = name, sortOrder = sortOrder, createdAt = System.currentTimeMillis()))
    }

    suspend fun renameList(list: ListEntity, newName: String) {
        listDao.update(list.copy(name = newName))
    }

    suspend fun reorderLists(lists: List<ListEntity>) {
        listDao.updateAll(lists.mapIndexed { index, list -> list.copy(sortOrder = index) })
    }

    suspend fun deleteList(list: ListEntity) {
        listDao.delete(list)
    }

    suspend fun insertItemAtTop(listId: Long, text: String): Long {
        val sortOrder = itemDao.minSortOrder(listId) - 1
        val globalSortOrder = itemDao.minGlobalSortOrder() - 1
        return itemDao.insert(
            ItemEntity(
                listId = listId,
                text = text,
                sortOrder = sortOrder,
                globalSortOrder = globalSortOrder,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun editItem(item: ItemEntity, newText: String) {
        itemDao.update(item.copy(text = newText))
    }

    suspend fun reorderItems(items: List<ItemEntity>) {
        itemDao.updateAll(items.mapIndexed { index, item -> item.copy(sortOrder = index) })
    }

    suspend fun reorderFlatItems(items: List<ItemWithListName>) {
        itemDao.updateAll(items.mapIndexed { index, entry -> entry.item.copy(globalSortOrder = index) })
    }

    suspend fun deleteItem(item: ItemEntity) {
        itemDao.delete(item)
    }

    suspend fun archiveItem(item: ItemEntity) {
        itemDao.update(item.copy(archived = true, archivedAt = System.currentTimeMillis()))
    }

    suspend fun restoreItem(item: ItemEntity) {
        itemDao.update(item.copy(archived = false))
    }

    // Wipes every list (cascading to every item) and reloads from a decoded backup file, all in
    // one transaction - a failure partway through rolls back completely, never leaving a mix of
    // old and new data (R4.7). This is REPLACE, not merge: existing rows are gone regardless of
    // whether the file also describes them. Every field (sortOrder, globalSortOrder, createdAt,
    // archived, archivedAt) is carried over verbatim, including sentinel values like a negative
    // sortOrder - this layer doesn't interpret ordering, only preserves it (R4.8). Each row's own
    // uid is preserved from the file untouched; a version-0 file (predates uid) gets a fresh one
    // per row instead - the one deliberate exception to uid's usual immutability, safe here only
    // because replace never matches rows against what it's replacing (R4.9/R4.10). Room assigns
    // fresh local `id`s regardless, since `id` was never part of the file format.
    suspend fun replaceAllWithBackup(export: BackupExport) {
        database.withTransaction {
            listDao.deleteAll()
            for (listExport in export.lists) {
                val listId = listDao.insert(
                    ListEntity(
                        name = listExport.name,
                        sortOrder = listExport.sortOrder,
                        createdAt = listExport.createdAt,
                        uid = listExport.uid ?: UUID.randomUUID().toString()
                    )
                )
                for (itemExport in listExport.items) {
                    itemDao.insert(
                        ItemEntity(
                            listId = listId,
                            text = itemExport.text,
                            sortOrder = itemExport.sortOrder,
                            globalSortOrder = itemExport.globalSortOrder,
                            createdAt = itemExport.createdAt,
                            archived = itemExport.archived,
                            archivedAt = itemExport.archivedAt,
                            uid = itemExport.uid ?: UUID.randomUUID().toString()
                        )
                    )
                }
            }
        }
    }
}
