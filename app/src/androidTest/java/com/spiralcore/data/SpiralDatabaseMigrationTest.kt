package com.spiralcore.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpiralDatabaseMigrationTest {
    private val dbName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SpiralDatabase::class.java.canonicalName!!,
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrationDeduplicatesAndPreservesEarliestTimestamp() {
        helper.createDatabase(dbName, 1).apply {
            execSQL("INSERT INTO categories(id, name, position) VALUES (1, 'Personal', 0)")
            execSQL(
                "INSERT INTO links(id, url, title, categoryId, createdAt, shellSeed) " +
                    "VALUES (1, 'https://example.com/', 'example.com', 1, 300, 11)",
            )
            execSQL(
                "INSERT INTO links(id, url, title, categoryId, createdAt, shellSeed) " +
                    "VALUES (2, 'https://example.com/', 'example.com', 1, 100, 12)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 2, true, SpiralDatabase.MIGRATION_1_2)
        db.query("SELECT id, createdAt FROM links WHERE url = 'https://example.com/'").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals(1L, cursor.getLong(0))
            assertEquals(100L, cursor.getLong(1))
        }
        db.close()
    }

    @Test
    fun migrationWorksWithEmptyLinksTable() {
        helper.createDatabase(dbName, 1).apply {
            execSQL("INSERT INTO categories(id, name, position) VALUES (1, 'Personal', 0)")
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 2, true, SpiralDatabase.MIGRATION_1_2)
        db.query("SELECT COUNT(*) FROM links").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
        db.close()
    }
}
