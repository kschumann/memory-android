package com.example.memory.data

import androidx.room.Embedded

data class ItemWithListName(
    @Embedded val item: ItemEntity,
    val listName: String
)
