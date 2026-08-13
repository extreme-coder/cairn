package app.cairn

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.runner.AndroidJUnitRunner
import app.cairn.core.database.CairnDatabase

/**
 * Swaps the app's `Application` for one that touches nothing on the device.
 *
 * An instrumented test that seeded the real `cairn.db` would be deleting
 * whoever is signed in on that device's unsent observations in order to check a
 * back stack. Everything under test — the routes, the screens, the queries — is
 * the same either way; what changes is where the rows live and whether a
 * Supabase client is built.
 */
public class CairnTestRunner : AndroidJUnitRunner() {

    override fun newApplication(
        classLoader: ClassLoader?,
        className: String?,
        context: Context?,
    ): Application = super.newApplication(
        classLoader,
        TestCairnApplication::class.java.name,
        context,
    )
}

public class TestCairnApplication : CairnApplication() {

    override val serverUrl: String get() = ""

    override val serverKey: String get() = ""

    override val database: CairnDatabase by lazy {
        Room.inMemoryDatabaseBuilder(this, CairnDatabase::class.java).build()
    }
}
