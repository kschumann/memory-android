package com.example.memory.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// No migration exists yet - the database is still at version 1. This test proves the harness
// itself works (it can find and parse schemas/com.example.memory.data.MemoryDatabase/1.json)
// before there's a real migration to test. When version 2 ships, add a test here that calls
// helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2).
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
}
