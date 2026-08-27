package com.example.memory.data

import androidx.room.Entity
import androidx.room.PrimaryKey

const val LIST_NAME_MAX_LENGTH = 100

@Entity(tableName = "lists")
data class ListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sortOrder: Int,
    val createdAt: Long
)
