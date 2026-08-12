package app.cairn.core.database

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection

/**
 * Runs [block] as one transaction.
 *
 * This is the driver-native path rather than room-ktx's `withTransaction`, which
 * reaches for a `SupportSQLiteOpenHelper` and therefore throws outright against
 * a database built with a `SQLiteDriver`. Sync's tests use the bundled native
 * driver on purpose — deferred foreign keys and unique-index conflicts have to
 * be the real ones — so the transaction helper has to work on both paths.
 *
 * Everything a sync delta touches goes through here: a partly applied delta with
 * an advanced cursor is indistinguishable, later, from data the server never
 * sent.
 */
public suspend fun <R> CairnDatabase.inTransaction(block: suspend () -> R): R =
    useWriterConnection { transactor ->
        transactor.immediateTransaction { block() }
    }
