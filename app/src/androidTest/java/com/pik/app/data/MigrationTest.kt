package com.pik.app.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pik.app.data.db.PikDatabase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Scaffold for migration tests. Schemas are exported to app/schemas and added to the
 * androidTest assets by the Room Gradle plugin. When version 2 is added, create the
 * database at version 1, insert rows, run [MigrationTestHelper.runMigrationsAndValidate]
 * with the new Migration and assert the data.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        PikDatabase::class.java,
    )

    @Test
    fun createVersion1AndOpenWithRoom() {
        helper.createDatabase(TEST_DB, 1).close()

        val db = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PikDatabase::class.java,
            TEST_DB,
        ).build()
        // Opening validates the schema against the current entities.
        db.openHelper.writableDatabase
        db.close()
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
