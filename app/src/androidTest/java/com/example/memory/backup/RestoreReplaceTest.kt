package com.example.memory.backup

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.memory.data.MemoryDatabase
import com.example.memory.data.MemoryRepository
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RestoreReplaceTest {

    private fun newRepository(): Pair<MemoryDatabase, MemoryRepository> {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MemoryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        return db to MemoryRepository(db.listDao(), db.itemDao(), db)
    }

    private fun uriForContent(context: Context, name: String, content: String): Uri =
        Uri.fromFile(File(context.cacheDir, name).apply { writeText(content) })

    // R4.3/R4.4: a file that isn't JSON at all is rejected with a specific reason, nothing written.
    @Test
    fun validatePickedFile_rejectsMalformedJson() = runBlocking {
        val (db, repository) = newRepository()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val backupManager = BackupManager(context, repository)
        try {
            val uri = uriForContent(context, "garbage.json", "this is not json at all {{{")
            val result = backupManager.validatePickedFile(uri)
            assertTrue(result is RestorePreflight.Rejected)
        } finally {
            db.close()
        }
    }

    // R4.3/R4.4: a formatVersion newer than this app understands is rejected, not silently misread.
    @Test
    fun validatePickedFile_rejectsAFormatVersionNewerThanSupported() = runBlocking {
        val (db, repository) = newRepository()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val backupManager = BackupManager(context, repository)
        try {
            val futureJson = """{"formatVersion": ${CURRENT_BACKUP_FORMAT_VERSION + 1}, "exportedAt": 1, "lists": []}"""
            val uri = uriForContent(context, "future.json", futureJson)
            val result = backupManager.validatePickedFile(uri)
            assertTrue(result is RestorePreflight.Rejected)
        } finally {
            db.close()
        }
    }

    // R4.5: preflight reports both the file's counts and the current local counts, so the
    // confirmation dialog can show them side by side.
    @Test
    fun validatePickedFile_reportsFileAndCurrentCountsSeparately() = runBlocking {
        val (db, repository) = newRepository()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val backupManager = BackupManager(context, repository)
        try {
            repository.insertListAtTop("Existing list").let { listId ->
                repository.insertItemAtTop(listId, "Existing item")
            }

            val fileJson = """
                {
                  "formatVersion": $CURRENT_BACKUP_FORMAT_VERSION,
                  "appVersion": "0.6.0",
                  "exportedAt": 1000,
                  "lists": [
                    {
                      "uid": "l1", "name": "A", "sortOrder": 0, "createdAt": 1,
                      "items": [
                        {"uid": "i1", "text": "x", "sortOrder": 0, "globalSortOrder": 0, "createdAt": 1},
                        {"uid": "i2", "text": "y", "sortOrder": 1, "globalSortOrder": 1, "createdAt": 1}
                      ]
                    }
                  ]
                }
            """.trimIndent()
            val uri = uriForContent(context, "valid.json", fileJson)

            val result = backupManager.validatePickedFile(uri)
            check(result is RestorePreflight.Ready) { "Expected Ready, got $result" }
            assertEquals(1, result.fileListCount)
            assertEquals(2, result.fileItemCount)
            assertEquals(1, result.currentListCount)
            assertEquals(1, result.currentItemCount)
        } finally {
            db.close()
        }
    }

    // R4.7: restore REPLACES - existing rows the file doesn't mention are gone afterward, not merged.
    @Test
    fun replaceAllWithBackup_removesExistingDataNotDescribedByTheFile() = runBlocking {
        val (db, repository) = newRepository()
        try {
            val existingListId = repository.insertListAtTop("Will be replaced")
            repository.insertItemAtTop(existingListId, "Will also be replaced")

            val replacement = BackupExport(
                formatVersion = CURRENT_BACKUP_FORMAT_VERSION,
                appVersion = "0.6.0",
                exportedAt = 1000L,
                lists = listOf(
                    ListExport(uid = "new-list", name = "Fresh", sortOrder = 0, createdAt = 1L, items = emptyList())
                )
            )
            repository.replaceAllWithBackup(replacement)

            val lists = repository.getAllListsWithItems()
            assertEquals(1, lists.size)
            assertEquals("Fresh", lists[0].list.name)
        } finally {
            db.close()
        }
    }

    // R4.8: sortOrder/globalSortOrder are carried through verbatim, including negative sentinel
    // values - never recomputed from the row's position in the file's items array.
    @Test
    fun replaceAllWithBackup_preservesSentinelSortValuesVerbatim() = runBlocking {
        val (db, repository) = newRepository()
        try {
            val export = BackupExport(
                formatVersion = CURRENT_BACKUP_FORMAT_VERSION,
                appVersion = "0.6.0",
                exportedAt = 1000L,
                lists = listOf(
                    ListExport(
                        uid = "l1",
                        name = "List",
                        sortOrder = -7,
                        createdAt = 1L,
                        items = listOf(
                            ItemExport(uid = "i1", text = "first", sortOrder = -1, globalSortOrder = -99, createdAt = 1L)
                        )
                    )
                )
            )
            repository.replaceAllWithBackup(export)

            val lists = repository.getAllListsWithItems()
            assertEquals(-7, lists[0].list.sortOrder)
            assertEquals(-1, lists[0].items[0].sortOrder)
            assertEquals(-99, lists[0].items[0].globalSortOrder)
        } finally {
            db.close()
        }
    }
}
