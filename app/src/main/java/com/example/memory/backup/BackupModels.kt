package com.example.memory.backup

import com.example.memory.data.ListWithItems
import kotlinx.serialization.Serializable

@Serializable
data class ItemExport(
    val text: String,
    val sortOrder: Int,
    val createdAt: Long
)

@Serializable
data class ListExport(
    val name: String,
    val sortOrder: Int,
    val createdAt: Long,
    val items: List<ItemExport>
)

@Serializable
data class BackupExport(
    val exportedAt: Long,
    val lists: List<ListExport>
)

fun ListWithItems.toExport(): ListExport = ListExport(
    name = list.name,
    sortOrder = list.sortOrder,
    createdAt = list.createdAt,
    items = items.sortedBy { it.sortOrder }.map { item ->
        ItemExport(text = item.text, sortOrder = item.sortOrder, createdAt = item.createdAt)
    }
)
