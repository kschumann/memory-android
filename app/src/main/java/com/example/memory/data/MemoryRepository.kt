package com.example.memory.data

import com.example.memory.backup.BackupExport
import java.util.UUID
import kotlinx.coroutines.flow.Flow

class MemoryRepository(
    private val listDao: ListDao,
    private val itemDao: ItemDao
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

    // Inserts every list/item from a decoded backup file as new rows. Each row's own uid is
    // preserved from the file untouched (falling back to a fresh one only for a version-0 file
    // that never had uid); Room assigns fresh local `id`s regardless, since `id` was never part
    // of the file format and a restored item's listId is wired to whatever `id` its parent list
    // just got. Callers decide when this is appropriate to call (e.g. into an empty database) -
    // this does not clear or merge with any existing data.
    suspend fun importBackup(export: BackupExport) {
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
