package app.cairn.core.database

import androidx.room.Room
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Locks the checked-in schema export to the entities that are actually compiled.
 *
 * There is only one version so far, so there is no migration to run yet. What
 * this catches is the thing that makes the first migration untestable: an entity
 * edited without re-exporting, which leaves `schemas/1.json` describing a
 * database that no longer exists and gives any future `MigrationTestHelper` a
 * false starting point.
 */
@RunWith(RobolectricTestRunner::class)
class SchemaExportTest {

    @get:Rule
    val temp: TemporaryFolder = TemporaryFolder()

    private val exported: Json by lazy { Json }

    @Test
    fun `the exported schema describes the database Room actually creates`() = runTest {
        val file = File(temp.newFolder(), "cairn.db")
        val db = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CairnDatabase::class.java,
            file.absolutePath,
        )
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Unconfined)
            .build()
        db.studies().observeAll().first()
        db.close()

        val schema = exported.parseToJsonElement(schemaFile().readText()).jsonObject
            .getValue("database").jsonObject

        BundledSQLiteDriver().open(file.absolutePath).use { connection ->
            assertEquals(
                schema.getValue("identityHash").jsonPrimitive.content,
                connection.single("select identity_hash from room_master_table"),
            )
            assertEquals(
                schema.getValue("entities").jsonArray
                    .map { it.jsonObject.getValue("tableName").jsonPrimitive.content }
                    .toSortedSet(),
                connection.all(
                    "select name from sqlite_master where type = 'table' and name not like 'sqlite_%' and name != 'room_master_table'",
                ).toSortedSet(),
            )
        }
    }

    @Test
    fun `the exported schema is at the version the database declares`() {
        val schema = exported.parseToJsonElement(schemaFile().readText()).jsonObject
            .getValue("database").jsonObject
        assertEquals(1, schema.getValue("version").jsonPrimitive.content.toInt())
    }

    private fun schemaFile(): File {
        val relative = "schemas/app.cairn.core.database.CairnDatabase/1.json"
        return listOf(File(relative), File("core/database/$relative"))
            .firstOrNull(File::exists)
            ?: error("No exported schema at $relative — is room.schemaDirectory still configured?")
    }
}

private fun SQLiteConnection.single(sql: String): String = all(sql).single()

private fun SQLiteConnection.all(sql: String): List<String> =
    prepare(sql).use { statement ->
        buildList {
            while (statement.step()) add(statement.getText(0))
        }
    }
