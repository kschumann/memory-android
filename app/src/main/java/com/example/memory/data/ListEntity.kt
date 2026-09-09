package com.example.memory.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

const val LIST_NAME_MAX_LENGTH = 100

@Entity(tableName = "lists", indices = [Index(value = ["uid"], unique = true)])
data class ListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sortOrder: Int,
    val createdAt: Long,
    // Stable identity for the export/import layer, independent of the Room-local `id`. Assigned
    // once at creation and never regenerated - not on edit, export, or re-import of a file that
    // already has one - so a restored row can be recognized as "the same row" across DB rebuilds.
    val uid: String = UUID.randomUUID().toString()
)
