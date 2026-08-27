package com.example.memory.data

import kotlinx.coroutines.flow.Flow

class MemoryRepository(
    private val listDao: ListDao,
    private val itemDao: ItemDao
) {
    fun observeLists(): Flow<List<ListEntity>> = listDao.observeLists()

    fun observeList(id: Long): Flow<ListEntity?> = listDao.observeList(id)

    fun observeItems(listId: Long): Flow<List<ItemEntity>> = itemDao.observeItems(listId)

    suspend fun getAllListsWithItems(): List<ListWithItems> = listDao.getAllListsWithItems()

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
        return itemDao.insert(ItemEntity(listId = listId, text = text, sortOrder = sortOrder, createdAt = System.currentTimeMillis()))
    }

    suspend fun editItem(item: ItemEntity, newText: String) {
        itemDao.update(item.copy(text = newText))
    }

    suspend fun reorderItems(items: List<ItemEntity>) {
        itemDao.updateAll(items.mapIndexed { index, item -> item.copy(sortOrder = index) })
    }

    suspend fun deleteItem(item: ItemEntity) {
        itemDao.delete(item)
    }
}
