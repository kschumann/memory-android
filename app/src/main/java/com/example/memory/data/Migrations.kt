package com.example.memory.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID

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

// Adds ItemEntity.archived/archivedAt (the swipe-right Archive feature). Existing rows default to
// archived=0 (not archived), so nothing that's currently visible disappears on upgrade.
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE items ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE items ADD COLUMN archivedAt INTEGER")
    }
}

// Adds ListEntity.uid/ItemEntity.uid: a stable, on-device-generated identity independent of the
// Room-local `id`, used only by the future export/import layer (never regenerated; parent/child
// relationships stay expressed via JSON nesting, not a uid reference). SQLite can't backfill a
// NOT NULL, uniquely-indexed column with a distinct value per existing row through a plain
// ALTER TABLE, so both tables are rebuilt: every row is read into memory first, `items` (the FK
// child) is dropped before `lists` is touched at all - SQLite refuses to drop/replace a table
// while another table still declares a live foreign key to it - then `lists` is rebuilt and only
// then is `items` recreated (against the now-final `lists`) and its rows reinserted.
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        data class OldList(val id: Long, val name: String, val sortOrder: Int, val createdAt: Long)
        data class OldItem(
            val id: Long,
            val listId: Long,
            val text: String,
            val sortOrder: Int,
            val globalSortOrder: Int,
            val createdAt: Long,
            val archived: Int,
            val archivedAt: Long?
        )

        val oldLists = mutableListOf<OldList>()
        db.query("SELECT id, name, sortOrder, createdAt FROM lists").use { cursor ->
            while (cursor.moveToNext()) {
                oldLists += OldList(
                    id = cursor.getLong(0),
                    name = cursor.getString(1),
                    sortOrder = cursor.getInt(2),
                    createdAt = cursor.getLong(3)
                )
            }
        }

        val oldItems = mutableListOf<OldItem>()
        db.query("SELECT id, listId, text, sortOrder, globalSortOrder, createdAt, archived, archivedAt FROM items")
            .use { cursor ->
                while (cursor.moveToNext()) {
                    oldItems += OldItem(
                        id = cursor.getLong(0),
                        listId = cursor.getLong(1),
                        text = cursor.getString(2),
                        sortOrder = cursor.getInt(3),
                        globalSortOrder = cursor.getInt(4),
                        createdAt = cursor.getLong(5),
                        archived = cursor.getInt(6),
                        archivedAt = if (cursor.isNull(7)) null else cursor.getLong(7)
                    )
                }
            }

        db.execSQL("DROP TABLE items")

        db.execSQL("DROP TABLE lists")
        db.execSQL(
            """
            CREATE TABLE lists (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `sortOrder` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `uid` TEXT NOT NULL
            )
            """.trimIndent()
        )
        for (list in oldLists) {
            db.execSQL(
                "INSERT INTO lists (id, name, sortOrder, createdAt, uid) VALUES (?, ?, ?, ?, ?)",
                arrayOf<Any?>(list.id, list.name, list.sortOrder, list.createdAt, UUID.randomUUID().toString())
            )
        }
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_lists_uid` ON `lists` (`uid`)")

        db.execSQL(
            """
            CREATE TABLE items (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `listId` INTEGER NOT NULL,
                `text` TEXT NOT NULL,
                `sortOrder` INTEGER NOT NULL,
                `globalSortOrder` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `archived` INTEGER NOT NULL,
                `archivedAt` INTEGER,
                `uid` TEXT NOT NULL,
                FOREIGN KEY(`listId`) REFERENCES `lists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        for (item in oldItems) {
            db.execSQL(
                """
                INSERT INTO items
                    (id, listId, text, sortOrder, globalSortOrder, createdAt, archived, archivedAt, uid)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    item.id,
                    item.listId,
                    item.text,
                    item.sortOrder,
                    item.globalSortOrder,
                    item.createdAt,
                    item.archived,
                    item.archivedAt,
                    UUID.randomUUID().toString()
                )
            )
        }
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_items_listId` ON `items` (`listId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_items_uid` ON `items` (`uid`)")
    }
}
