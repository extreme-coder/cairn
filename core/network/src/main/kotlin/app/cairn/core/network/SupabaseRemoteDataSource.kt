package app.cairn.core.network

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.PostgrestQueryBuilder
import io.github.jan.supabase.postgrest.query.filter.PostgrestFilterBuilder
import io.github.jan.supabase.postgrest.result.PostgrestResult
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import java.io.IOException

/**
 * Builds the Supabase client.
 *
 * [key] must be the **publishable** key. RLS is the security boundary and the
 * APK ships this key to every device; a `service_role` key in a shipped binary
 * would be a total compromise.
 *
 * [engine] is injectable so tests can drive a `MockEngine` and assert on the
 * request that would have gone out.
 *
 * **Storage is opt-in through [sessions].** With no session manager supplied the
 * session lives in memory only and dies with the process — which is what tests
 * want, and what a pure JVM caller has to have, because supabase-kt's own JVM
 * fallback writes a token to a file beside the process. Pass a
 * [StoredSessionManager] and the library loads, refreshes and re-saves the
 * session itself; how those bytes are protected is that store's problem, not
 * this module's.
 */
public fun cairnSupabaseClient(
    url: String,
    key: String,
    engine: HttpClientEngine? = null,
    sessions: StoredSessionManager? = null,
): SupabaseClient = createSupabaseClient(supabaseUrl = url, supabaseKey = key) {
    install(Auth) {
        if (sessions != null) sessionManager = sessions
        autoLoadFromStorage = sessions != null
        autoSaveToStorage = sessions != null
        alwaysAutoRefresh = true
    }
    install(Postgrest)
    if (engine != null) httpEngine = engine
}

public class SupabaseRemoteDataSource(
    private val client: SupabaseClient,
    sessions: StoredSessionManager? = null,
) : RemoteDataSource {

    override val sessionState: Flow<SessionState> =
        combine(
            client.auth.sessionStatus,
            sessions?.knownUserId ?: flowOf(null),
        ) { status, known -> sessionStateOf(status, known) }

    /**
     * A refused sign-in and an unreachable one are told apart by which exception
     * arrives, not by inspecting a message. `RestException` means the server
     * answered; `HttpRequestException` and ktor's timeout both extend
     * `IOException`, which is the whole family of "the request never got there".
     */
    override suspend fun signIn(email: String, password: String): SignInOutcome = try {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        SignInOutcome.Success
    } catch (refused: RestException) {
        SignInOutcome.Rejected(refused.description ?: refused.error)
    } catch (_: IOException) {
        SignInOutcome.Unreachable
    }

    /**
     * The revoke is best-effort; ending the session locally is not.
     *
     * `signOut()` calls the server and throws when it cannot be reached, which
     * would otherwise leave a device signed in precisely when someone is trying
     * to hand it to a colleague. `clearSession()` drops the in-memory session and
     * deletes the stored one, so the local half always happens.
     */
    override suspend fun signOut() {
        runCatching { client.auth.signOut() }
        client.auth.clearSession()
    }

    override suspend fun currentUserId(): String? = client.auth.currentUserOrNull()?.id

    override suspend fun studies(since: String?, limit: Int): List<StudyDto> =
        client.from("studies").page(since, limit).decodeList()

    override suspend fun members(studyId: String, since: String?, limit: Int): List<StudyMemberDto> =
        client.from("study_members").page(since, limit) { eq("study_id", studyId) }.decodeList()

    override suspend fun forms(studyId: String, since: String?, limit: Int): List<FormDto> =
        client.from("forms").page(since, limit) { eq("study_id", studyId) }.decodeList()

    override suspend fun formVersions(
        formIds: List<String>,
        since: String?,
        limit: Int,
    ): List<FormVersionDto> {
        if (formIds.isEmpty()) return emptyList()
        return client.from("form_versions")
            .page(since, limit) { isIn("form_id", formIds) }
            .decodeList()
    }

    override suspend fun participants(
        studyId: String,
        since: String?,
        limit: Int,
    ): List<ParticipantDto> =
        client.from("participants").page(since, limit) { eq("study_id", studyId) }.decodeList()

    override suspend fun translations(
        formVersionIds: List<String>,
        since: String?,
        limit: Int,
    ): List<FormTranslationDto> {
        if (formVersionIds.isEmpty()) return emptyList()
        return client.from("form_translations")
            .page(since, limit) { isIn("form_version_id", formVersionIds) }
            .decodeList()
    }

    override suspend fun submissions(
        studyId: String,
        since: String?,
        limit: Int,
    ): List<SubmissionDto> =
        client.from("submissions").page(since, limit) { eq("study_id", studyId) }.decodeList()

    /**
     * `updated_at` is not sent. The server owns it — the touch trigger overwrites
     * whatever arrives, on insert as well as update — and it is what
     * last-write-wins compares. Stripping it here means a device with a skewed
     * clock cannot even attempt to win every conflict.
     */
    override suspend fun push(submissions: List<SubmissionDto>): List<SubmissionDto> {
        if (submissions.isEmpty()) return emptyList()
        return client.from("submissions").upsert(
            submissions.map { it.copy(updatedAt = null) },
        ) {
            onConflict = "collected_by,client_id"
            select()
        }.decodeList()
    }
}

/**
 * The one paging shape every read uses: whatever scoping the table needs, then
 * `updated_at > cursor`, oldest first, capped.
 *
 * Ordering and the cursor comparison must agree or a page boundary silently
 * drops rows, which is the kind of bug that shows up as missing data months
 * later rather than as an error.
 */
private suspend fun PostgrestQueryBuilder.page(
    since: String?,
    limit: Int,
    scope: PostgrestFilterBuilder.() -> Unit = {},
): PostgrestResult = select {
    filter {
        scope()
        if (since != null) gt("updated_at", since)
    }
    order("updated_at", Order.ASCENDING)
    limit(limit.toLong())
}
