package app.cairn

import android.app.Application
import androidx.room.Room
import app.cairn.core.database.CairnDatabase

/**
 * Holds the one Room database.
 *
 * Hand-wired rather than injected. There is one screen and one dependency graph
 * edge worth naming so far; Koin arrives when there is something to wire.
 */
public class CairnApplication : Application() {

    public val database: CairnDatabase by lazy {
        Room.databaseBuilder(this, CairnDatabase::class.java, CairnDatabase.NAME).build()
    }
}
