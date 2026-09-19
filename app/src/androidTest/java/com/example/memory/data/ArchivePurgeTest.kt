package com.example.memory.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArchivePurgeTest {
    private lateinit var db: MemoryDatabase
    private lateinit var repository: MemoryRepository

    private val now = 10 * ARCHIVE_RETENTION_MS
    private val hour = 60 * 60 * 1000L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MemoryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = MemoryRepository(db.listDao(), db.itemDao(), db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun insertItem(listId: Long, text: String, archived: Boolean, archivedAt: Long?) {
        db.itemDao().insert(
            ItemEntity(
                listId = listId,
                text = text,
                sortOrder = 0,
                globalSortOrder = 0,
                createdAt = 0L,
                archived = archived,
                archivedAt = archivedAt
            )
        )
    }

    private suspend fun remainingTexts(listId: Long): Set<String> =
        (repository.observeItems(listId).first() + repository.observeArchivedItems(listId).first())
            .map { it.text }
            .toSet()

    @Test
    fun purgesOnlyArchivedItemsOlderThanThreeDays() = runBlocking {
        val listId = repository.insertListAtTop("L")
        insertItem(listId, "active", archived = false, archivedAt = null)
        insertItem(listId, "old-archived", archived = true, archivedAt = now - ARCHIVE_RETENTION_MS - hour)
        insertItem(listId, "recent-archived", archived = true, archivedAt = now - ARCHIVE_RETENTION_MS + hour)
        insertItem(listId, "null-archivedAt", archived = true, archivedAt = null)

        val purged = repository.purgeExpiredArchivedItems(now)

        assertEquals(1, purged)
        assertEquals(setOf("active", "recent-archived", "null-archivedAt"), remainingTexts(listId))
    }

    @Test
    fun restoredItemIsNeverPurged() = runBlocking {
        val listId = repository.insertListAtTop("L")
        insertItem(listId, "old-archived", archived = true, archivedAt = now - ARCHIVE_RETENTION_MS - hour)
        repository.restoreItem(repository.observeArchivedItems(listId).first().single())

        val purged = repository.purgeExpiredArchivedItems(now)

        assertEquals(0, purged)
        assertEquals(setOf("old-archived"), remainingTexts(listId))
    }
}
