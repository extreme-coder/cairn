package app.cairn.feature.review

import app.cairn.core.database.dao.SubmissionDao
import app.cairn.core.network.RemoteDataSource
import app.cairn.core.network.ReviewWriteOutcome
import kotlin.time.Instant

/** What a lock, a void or a restore came back as, in the words the screen shows. */
public sealed interface ReviewOutcome {

    public data object Applied : ReviewOutcome

    /** The server answered and did not make the change. Carries its own copy. */
    public data class Refused(public val message: String) : ReviewOutcome

    /**
     * The server could not be reached. A separate answer from a refusal, the
     * same way a failed sign-in is — telling a coordinator on a train that their
     * role does not allow something is the wrong sentence, and they would act on
     * it.
     */
    public data object Offline : ReviewOutcome
}

/**
 * Locks, voids and restores a submission on the server, then catches the local
 * copy up.
 *
 * **The one place in the app that writes to the server directly**, and it is
 * worth saying why the rest does not. Everything a collector does is queued into
 * Room and drained later by `:core:sync`, because a collector is the only holder
 * of an observation and their device must accept work with no network at all. A
 * coordinator locking a submission is the opposite case: the row belongs to
 * someone else, the change is one column, and the device's copy of the payload
 * is a snapshot that may already be stale. Queuing that would mean pushing a
 * whole row under another person's `(collected_by, client_id)` key and letting
 * last-write-wins decide — which would silently discard an amendment made in
 * between.
 *
 * So a review action needs the network, and says so when there is none rather
 * than pretending to have queued. `DESIGN.md` puts the coordinator indoors,
 * scanning; the collector in the valley is the one this app bends for.
 */
public class ReviewRepository(
    private val submissions: SubmissionDao,
    /**
     * Null on a build with no server configured, where every action is
     * [ReviewOutcome.Offline] because there is nowhere to send it. Unreachable
     * in practice — the Sign in screen refuses first — but it is what lets
     * `:app` build its graph without branching on it.
     */
    private val remote: RemoteDataSource?,
) {

    public suspend fun lock(collectedBy: String, clientId: String, serverId: String, now: Instant): ReviewOutcome =
        apply(collectedBy, clientId) { it.lock(serverId, now.wire()) }

    public suspend fun void(collectedBy: String, clientId: String, serverId: String, now: Instant): ReviewOutcome =
        apply(collectedBy, clientId) { it.setVoided(serverId, now.wire()) }

    public suspend fun restore(collectedBy: String, clientId: String, serverId: String): ReviewOutcome =
        apply(collectedBy, clientId) { it.setVoided(serverId, null) }

    /**
     * Writes back exactly what the server echoed, never what was asked for.
     *
     * The request carried a device timestamp; the row that comes back carries
     * whatever the server actually stored, including the `updated_at` its own
     * trigger stamped. Assuming the write landed as sent is how a local copy
     * drifts from the server without anything failing.
     */
    private suspend fun apply(
        collectedBy: String,
        clientId: String,
        write: suspend (RemoteDataSource) -> ReviewWriteOutcome,
    ): ReviewOutcome = when (val outcome = remote?.let { write(it) } ?: ReviewWriteOutcome.Unreachable) {
        is ReviewWriteOutcome.Unreachable -> ReviewOutcome.Offline
        is ReviewWriteOutcome.Refused -> ReviewOutcome.Refused(outcome.reason)
        is ReviewWriteOutcome.Applied -> {
            val stored = outcome.submission
            submissions.applyReview(
                collectedBy = collectedBy,
                clientId = clientId,
                lockedAt = stored.lockedAt?.toInstant(),
                deletedAt = stored.deletedAt?.toInstant(),
                updatedAt = stored.updatedAt?.toInstant() ?: Instant.DISTANT_PAST,
            )
            ReviewOutcome.Applied
        }
    }
}

/**
 * The device's clock, in the shape PostgREST parses.
 *
 * A timestamp has to be sent at all because PostgREST cannot be asked to
 * evaluate `now()` — see [RemoteDataSource.lock] for why that is tolerable for
 * `locked_at` and would not be for `updated_at`.
 */
private fun Instant.wire(): String = toString()

/**
 * Wire text to a stored instant, the same conversion `:core:sync`'s mappers
 * make, and safe for the same reason: nothing here round-trips microseconds,
 * because the sync cursor keeps the server's exact string elsewhere.
 *
 * Unparseable text is dropped rather than thrown. A timestamp arriving in a
 * shape this build does not know should not take down the screen reading it —
 * and the row is already correct on the server, which is the copy that counts.
 */
private fun String.toInstant(): Instant? = runCatching { Instant.parse(this) }.getOrNull()
