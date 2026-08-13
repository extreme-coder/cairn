package app.cairn.core.sync

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Runs [SyncEngine] on WorkManager's terms.
 *
 * Deliberately thin. Everything that could be wrong about syncing is in
 * [SyncEngine], where it can be tested against a fake server on the JVM; what is
 * left here is scheduling policy and the translation from an exception to a
 * [Result].
 *
 * The engine's dependencies are not injectable through a `Worker` constructor,
 * so they come from [SyncDependencies], which the application sets once at
 * start-up. That is the same hand-wiring `:app` already uses for the database —
 * Koin arrives when there is more than one graph worth naming.
 */
public class SyncWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val engine = SyncDependencies.engine
            ?: return Result.failure(reason("sync was never configured"))

        return try {
            val outcome = engine.sync()
            SyncStatus.succeeded()
            /*
             * Recorded here rather than inside the engine so that "last synced"
             * means exactly what `SyncStatus.succeeded()` means, and cannot come
             * to mean something subtly different. A push that could not send
             * throws out of `sync()` and lands in the retry branch below, so
             * neither line is reached — which is correct: a device with
             * submissions still in the queue has not synced.
             */
            SyncDependencies.log?.record(Instant.fromEpochMilliseconds(System.currentTimeMillis()))
            Log.i(TAG, "pushed ${outcome.pushed}, failed ${outcome.failed}, pulled ${outcome.pulledTotal}")
            Result.success(
                workDataOf(
                    KEY_PUSHED to outcome.pushed,
                    KEY_FAILED to outcome.failed,
                    KEY_PULLED to outcome.pulledTotal,
                ),
            )
        } catch (_: SyncException.NotSignedIn) {
            Log.i(TAG, "no signed-in user; nothing to sync")
            Result.success()
        } catch (stalled: SyncException.CursorStalled) {
            // Retrying cannot help: the next run asks the same question and gets
            // the same page. Failing stops the backoff from hiding it.
            Log.e(TAG, "sync is stuck and needs a compound cursor", stalled)
            Result.failure(reason(stalled.message))
        } catch (problem: Exception) {
            Log.w(TAG, "sync failed; will retry", problem)
            Result.retry()
        }
    }

    private fun reason(message: String?) = workDataOf(KEY_REASON to (message ?: "unknown"))

    public companion object {
        private const val TAG = "CairnSync"

        public const val PERIODIC: String = "cairn-sync-periodic"
        public const val EXPEDITED: String = "cairn-sync-now"

        public const val KEY_PUSHED: String = "pushed"
        public const val KEY_FAILED: String = "failed"
        public const val KEY_PULLED: String = "pulled"
        public const val KEY_REASON: String = "reason"

        /**
         * Fifteen minutes is WorkManager's floor for periodic work, not a choice.
         * The interval that matters in the field is the expedited one below —
         * a device that has been out of signal all day syncs when it reconnects,
         * not up to fifteen minutes later.
         */
        private val INTERVAL = 15.minutes
        private val BACKOFF = 30.seconds

        private val onNetwork = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /**
         * Idempotent: `KEEP` means re-registering on every cold start does not
         * reset the interval, which would let an app that is opened often never
         * reach its own schedule.
         */
        public fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(
                INTERVAL.inWholeMinutes,
                TimeUnit.MINUTES,
            )
                .setConstraints(onNetwork)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF.inWholeSeconds, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        /**
         * A one-shot for "the collector just saved something" and for reconnects.
         *
         * `APPEND_OR_REPLACE` rather than `KEEP`: two saves in a minute should
         * result in a sync that includes both, and replacing a run that has not
         * started yet is cheaper than queueing a second one behind it.
         */
        public fun syncNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(onNetwork)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF.inWholeSeconds, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                EXPEDITED,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request,
            )
        }

        public fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
            WorkManager.getInstance(context).cancelUniqueWork(EXPEDITED)
        }
    }
}

/**
 * The one graph edge a `Worker` cannot be handed through its constructor.
 *
 * WorkManager instantiates workers reflectively, so anything they need has to be
 * reachable statically or through a `WorkerFactory`. A factory is the tidier
 * answer and is worth writing when there is a second worker; with one, this is
 * an honest single assignment rather than a framework to hold it.
 */
public object SyncDependencies {

    @Volatile
    public var engine: SyncEngine? = null

    /**
     * Optional, and null in every test that does not care when a sync happened.
     * A worker with no log still syncs; it just cannot say when it last did,
     * which is the Settings screen's problem and not the queue's.
     */
    @Volatile
    public var log: SyncLog? = null

    public fun install(engine: SyncEngine, log: SyncLog? = null) {
        this.engine = engine
        this.log = log
    }
}
