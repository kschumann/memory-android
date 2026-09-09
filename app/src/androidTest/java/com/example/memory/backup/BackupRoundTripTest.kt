package com.example.memory.backup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.memory.data.MemoryDatabase
import com.example.memory.data.MemoryRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

// R2.9: export -> restore into an empty database -> export again must reproduce the same content
// (aside from exportedAt, and appVersion if the two exports were taken from different app builds
// - constant here since both happen within one test run). Compared as decoded objects rather than
// raw JSON bytes, since that's what actually matters and isn't sensitive to incidental formatting.
@RunWith(AndroidJUnit4::class)
class BackupRoundTripTest {

    @Test
    fun exportRestoreExport_matchesApartFromExportedAt() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MemoryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repository = MemoryRepository(db.listDao(), db.itemDao())

        val original = BackupExport(
            formatVersion = CURRENT_BACKUP_FORMAT_VERSION,
            appVersion = "0.6.0",
            exportedAt = 1_000L,
            lists = listOf(
                ListExport(
                    uid = "list-uid-1",
                    name = "Groceries",
                    sortOrder = 0,
                    createdAt = 100L,
                    items = listOf(
                        ItemExport(
                            uid = "item-uid-1",
                            text = "Milk",
                            sortOrder = 0,
                            globalSortOrder = 0,
                            createdAt = 100L
                        ),
                        ItemExport(
                            uid = "item-uid-2",
                            text = "Eggs",
                            sortOrder = 1,
                            globalSortOrder = 1,
                            createdAt = 110L,
                            archived = true,
                            archivedAt = 150L
                        )
                    )
                ),
                ListExport(
                    uid = "list-uid-2",
                    name = "Chores",
                    sortOrder = 1,
                    createdAt = 200L,
                    items = listOf(
                        ItemExport(
                            uid = "item-uid-3",
                            text = "Trash",
                            sortOrder = 0,
                            globalSortOrder = 2,
                            createdAt = 200L
                        )
                    )
                )
            )
        )

        try {
            repository.importBackup(original)

            val restored = BackupExport(
                formatVersion = CURRENT_BACKUP_FORMAT_VERSION,
                appVersion = original.appVersion,
                exportedAt = 2_000L, // deliberately different - allowed to differ per R2.9
                lists = repository.getAllListsWithItems().map { it.toExport() }
            )

            assertEquals(original.copy(exportedAt = 0), restored.copy(exportedAt = 0))
        } finally {
            db.close()
        }
    }

    @Test
    fun restoreFrom_mintsFreshUidsForAVersion0FileThatNeverHadOne() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MemoryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repository = MemoryRepository(db.listDao(), db.itemDao())
        val backupManager = BackupManager(ApplicationProvider.getApplicationContext(), repository)

        // No formatVersion, appVersion, or uid keys at all - exactly what every file written
        // before this feature existed looks like.
        val version0Json = """
            {
              "exportedAt": 500,
              "lists": [
                {
                  "name": "Old list",
                  "sortOrder": 0,
                  "createdAt": 50,
                  "items": [
                    { "text": "Old item", "sortOrder": 0, "globalSortOrder": 0, "createdAt": 50 }
                  ]
                }
              ]
            }
        """.trimIndent()

        try {
            backupManager.restoreFrom(version0Json)

            val lists = repository.getAllListsWithItems()
            assertEquals(1, lists.size)
            assertEquals(false, lists[0].list.uid.isBlank())
            assertEquals(1, lists[0].items.size)
            assertEquals(false, lists[0].items[0].uid.isBlank())
        } finally {
            db.close()
        }
    }
}
