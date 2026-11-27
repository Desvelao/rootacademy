package app.root_academy.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Harness for schema migrations. When the database version is bumped, add the new `Migration` to the Room builder and
 * a test below that creates the previous version, inserts rows, migrates and checks them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {
    @get:Rule val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    /** Version 1 as exported to app/schemas must open and validate against the current entities. */
    @Test fun version1SchemaMatchesTheExportedJson() {
        helper.createDatabase("migration-test", 1).close()
        helper.runMigrationsAndValidate("migration-test", 1, true).close()
    }
}
