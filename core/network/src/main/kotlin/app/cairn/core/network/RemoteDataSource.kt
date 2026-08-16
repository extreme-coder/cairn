package app.cairn.core.network

import kotlinx.coroutines.flow.Flow

/**
 * Everything Cairn asks of a server, in one interface.
 *
 * `:core:sync` depends on this and not on Supabase, so the Firestore adapter on
 * the cut list stays an adapter rather than a rewrite, and so sync can be tested
 * against a fake without a network.
 *
 * **Every read is incremental and every read pages the same way.** Pass the
 * server's own `updated_at` string from the last row of the previous page back
 * as [since]; null starts from the beginning. Rows come back oldest first, and a
 * page shorter than the limit means the caller has caught up.
 *
 * That uniformity only became possible on 2026-08-11, when `updated_at` and its
 * touch triggers were added to the five reference tables. Before that they had
 * `created_at` only, which cannot express "this row changed".
 */
public interface RemoteDataSource {

    /**
     * Who this device is signed in as, as it changes.
     *
     * The one source of that answer. Emits [SessionState.Unknown] until storage
     * has been read, so a cold start does not flash a signed-out state at
     * someone who is signed in.
     */
    public val sessionState: Flow<SessionState>

    public suspend fun signIn(email: String, password: String): SignInOutcome

    /**
     * Ends the session on this device, revoking it server-side if that is
     * possible. It must end locally either way — a collector handing the phone
     * over is not always in coverage, and "sign out failed, you are still signed
     * in" is the wrong answer to give them.
     */
    public suspend fun signOut()

    /**
     * Null when there is no usable access token — which includes a session that
     * is signed in but [SessionState.Stale]. Sync asks this, and the honest
     * answer while a refresh is failing is that there is nothing to sync with.
     */
    public suspend fun currentUserId(): String?

    public suspend fun studies(since: String? = null, limit: Int = PAGE): List<StudyDto>

    public suspend fun members(
        studyId: String,
        since: String? = null,
        limit: Int = PAGE,
    ): List<StudyMemberDto>

    public suspend fun forms(
        studyId: String,
        since: String? = null,
        limit: Int = PAGE,
    ): List<FormDto>

    public suspend fun formVersions(
        formIds: List<String>,
        since: String? = null,
        limit: Int = PAGE,
    ): List<FormVersionDto>

    public suspend fun participants(
        studyId: String,
        since: String? = null,
        limit: Int = PAGE,
    ): List<ParticipantDto>

    public suspend fun translations(
        formVersionIds: List<String>,
        since: String? = null,
        limit: Int = PAGE,
    ): List<FormTranslationDto>

    public suspend fun submissions(
        studyId: String,
        since: String? = null,
        limit: Int = PAGE,
    ): List<SubmissionDto>

    /**
     * Upserts on `(collected_by, client_id)` and returns what the server stored,
     * including the `id` it assigned and its authoritative `updated_at`.
     *
     * Replaying a push is a no-op rather than a duplicate. That is the whole
     * reason `client_id` is minted when a form opens.
     */
    public suspend fun push(submissions: List<SubmissionDto>): List<SubmissionDto>

    /**
     * Marks a submission verified, after which no client may amend it.
     *
     * **One-way from here.** The `amend unlocked` policy has `locked_at is null`
     * in its `using` clause, so a locked row matches no client UPDATE at all —
     * including one that would clear the column. That is the point of locking
     * and it is enforced by the database rather than by this app declining to
     * offer a button. There is deliberately no `unlock` on this interface: a
     * method that can only ever be refused is worse than an absent one. Repair
     * goes through a direct database session, which the invariants trigger lets
     * past because `auth.uid()` is null there.
     *
     * [at] is the device's clock, which is the only clock a PostgREST caller
     * has — the server cannot be asked to evaluate `now()` through a JSON body.
     * It is safe here in a way it would not be for `updated_at`: nothing
     * compares two `locked_at` values, RLS reads the column only as null or
     * not, and `submission_audit.changed_at` is stamped server-side and is the
     * authoritative record of when this happened.
     */
    public suspend fun lock(id: String, at: String): ReviewWriteOutcome

    /**
     * Excludes a submission from analysis, or puts it back.
     *
     * [at] null restores it. Both directions are reachable because `deleted_at`
     * appears in no policy predicate, unlike `locked_at` — a void is a judgement
     * about the observation and judgements get revised, whereas a lock is a
     * statement that the row will never change again.
     *
     * Neither direction works on a locked row, for the same reason nothing else
     * does.
     */
    public suspend fun setVoided(id: String, at: String?): ReviewWriteOutcome

    public companion object {
        public const val PAGE: Int = 500
    }
}

/**
 * What came back from a lock, a void or a restore.
 *
 * Explicit rather than an exception for the same reason [SignInOutcome] is: the
 * three answers need three different sentences on screen, and the two failures
 * are told apart by which exception arrived rather than by reading a message —
 * a distinction that would be lost the moment it was flattened into a throw.
 */
public sealed interface ReviewWriteOutcome {

    /** The server applied it and echoed the row it stored. */
    public data class Applied(public val submission: SubmissionDto) : ReviewWriteOutcome

    /** The server answered and the row was not changed. */
    public data class Refused(public val reason: String) : ReviewWriteOutcome

    /** The server could not be reached at all. */
    public data object Unreachable : ReviewWriteOutcome
}
