package com.example.memory.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Adds ItemEntity.globalSortOrder (the cross-list rank backing the flattened List View).
// Existing rows are backfilled with a sequential 0-based rank matching the app's current
// natural ordering - each list in its own sortOrder, and within a list each item in its own
// sortOrder - so upgrading doesn't reshuffle anything a user already sees.
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE items ADD COLUMN globalSortOrder INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            """
            UPDATE items SET globalSortOrder = (
                SELECT COUNT(*)
                FROM items AS other
                INNER JOIN lists AS otherList ON other.listId = otherList.id
                INNER JOIN lists AS thisList ON items.listId = thisList.id
                WHERE otherList.sortOrder < thisList.sortOrder
                   OR (otherList.sortOrder = thisList.sortOrder AND other.sortOrder < items.sortOrder)
                   OR (otherList.sortOrder = thisList.sortOrder AND other.sortOrder = items.sortOrder AND other.id < items.id)
            )
            """.trimIndent()
        )
    }
}
