package app.cairn.core.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers

/**
 * An in-memory database for unit tests. No emulator, no device.
 *
 * Robolectric supplies the `Context` Room's Android builder insists on, but the
 * storage engine is the bundled native SQLite rather than Robolectric's, so
 * these tests exercise the same SQLite that ships in the app.
 */
internal fun testDatabase(): CairnDatabase =
    Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        CairnDatabase::class.java,
    )
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.Unconfined)
        .build()
