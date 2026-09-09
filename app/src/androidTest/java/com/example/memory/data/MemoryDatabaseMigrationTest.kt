package com.example.memory.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MemoryDatabaseMigrationTest {

    private val testDb = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MemoryDatabase::class.java,
    )

    @Test
    fun createsVersion1FromExportedSchema() {
        helper.createDatabase(testDb, 1).close()
    }

    @Test
    fun migrate1To2_addsGlobalSortOrderAndBackfillsSequentialRank() {
        helper.createDatabase(testDb, 1).apply {
            execSQL("INSERT INTO lists (id, name, sortOrder, createdAt) VALUES (1, 'List A', 0, 100)")
            execSQL("INSERT INTO lists (id, name, sortOrder, createdAt) VALUES (2, 'List B', 1, 200)")
            execSQL("INSERT INTO items (id, listId, text, sortOrder, createdAt) VALUES (1, 1, 'A-first', 0, 100)")
            execSQL("INSERT INTO items (id, listId, text, sortOrder, createdAt) VALUES (2, 1, 'A-second', 1, 100)")
            execSQL("INSERT INTO items (id, listId, text, sortOrder, createdAt) VALUES (3, 2, 'B-first', 0, 100)")
            close()
        }

        val migrated = helper.runMigrationsAndValidate(testDb, 2, true, MIGRATION_1_2)

        val ranks = mutableMapOf<Long, Int>()
        migrated.query("SELECT id, globalSortOrder FROM items ORDER BY id").use { cursor ->
            while (cursor.moveToNext()) {
                ranks[cursor.getLong(0)] = cursor.getInt(1)
            }
        }
        assertEquals(mapOf(1L to 0, 2L to 1, 3L to 2), ranks)
    }

    @Test
    fun migrate2To3_addsArchivedDefaultingToFalseForExistingRows() {
        helper.createDatabase(testDb, 2).apply {
            execSQL("INSERT INTO lists (id, name, sortOrder, createdAt) VALUES (1, 'List A', 0, 100)")
            execSQL(
                "INSERT INTO items (id, listId, text, sortOrder, globalSortOrder, createdAt) VALUES (1, 1, 'A-first', 0, 0, 100)"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(testDb, 3, true, MIGRATION_2_3)

        migrated.query("SELECT archived, archivedAt FROM items WHERE id = 1").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
            assertEquals(true, cursor.isNull(1))
        }
    }

    @Test
    fun migrate3To4_backfillsDistinctUidsAndPreservesParentChildRelationships() {
        helper.createDatabase(testDb, 3).apply {
            execSQL("INSERT INTO lists (id, name, sortOrder, createdAt) VALUES (1, 'List A', 0, 100)")
            execSQL("INSERT INTO lists (id, name, sortOrder, createdAt) VALUES (2, 'List B', 1, 200)")
            execSQL(
                "INSERT INTO items (id, listId, text, sortOrder, globalSortOrder, createdAt, archived, archivedAt) " +
                    "VALUES (1, 1, 'A-first', 0, 0, 100, 0, NULL)"
            )
            execSQL(
                "INSERT INTO items (id, listId, text, sortOrder, globalSortOrder, createdAt, archived, archivedAt) " +
                    "VALUES (2, 1, 'A-second', 1, 1, 100, 1, 150)"
            )
            execSQL(
                "INSERT INTO items (id, listId, text, sortOrder, globalSortOrder, createdAt, archived, archivedAt) " +
                    "VALUES (3, 2, 'B-first', 0, 2, 100, 0, NULL)"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(testDb, 4, true, MIGRATION_3_4)

        val listUids = mutableMapOf<Long, String?>()
        migrated.query("SELECT id, uid FROM lists ORDER BY id").use { cursor ->
            while (cursor.moveToNext()) {
                listUids[cursor.getLong(0)] = cursor.getString(1)
            }
        }
        assertEquals(setOf(1L, 2L), listUids.keys)
        listUids.values.forEach { uid -> assertEquals(false, uid.isNullOrBlank()) }
        assertEquals(listUids.values.toSet().size, listUids.size) // distinct within the table

        val itemRows = mutableMapOf<Long, Pair<String?, Long>>() // id -> (uid, listId)
        migrated.query("SELECT id, uid, listId FROM items ORDER BY id").use { cursor ->
            while (cursor.moveToNext()) {
                itemRows[cursor.getLong(0)] = cursor.getString(1) to cursor.getLong(2)
            }
        }
        assertEquals(setOf(1L, 2L, 3L), itemRows.keys)
        itemRows.values.forEach { (uid, _) -> assertEquals(false, uid.isNullOrBlank()) }
        assertEquals(itemRows.values.map { it.first }.toSet().size, itemRows.size) // distinct within the table

        // Parent/child relationships (item -> its list) survive the rebuild unchanged.
        assertEquals(1L, itemRows.getValue(1).second)
        assertEquals(1L, itemRows.getValue(2).second)
        assertEquals(2L, itemRows.getValue(3).second)
    }
}
