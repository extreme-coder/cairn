package app.cairn.core.session

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import app.cairn.core.database.CairnDatabase
import app.cairn.core.database.entity.FormEntity
import app.cairn.core.database.entity.FormVersionEntity
import app.cairn.core.database.entity.StudyEntity
import app.cairn.core.database.entity.SubmissionEntity
import app.cairn.core.model.SyncState
import app.cairn.core.network.FormDto
import app.cairn.core.network.FormTranslationDto
import app.cairn.core.network.FormVersionDto
import app.cairn.core.network.ParticipantDto
import app.cairn.core.network.RemoteDataSource
import app.cairn.core.network.ReviewWriteOutcome
import app.cairn.core.network.SessionState
import app.cairn.core.network.SignInOutcome
import app.cairn.core.network.StudyDto
import app.cairn.core.network.StudyMemberDto
import app.cairn.core.network.SubmissionDto
import app.cairn.core.sync.SyncCursors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

internal object Ids {
    const val ADAKU = "55555555-5555-5555-5555-555555555551"
    const val TOMAS = "55555555-5555-5555-5555-555555555552"
    const val STUDY = "11111111-1111-1111-1111-111111111111"
    const val FORM = "22222222-2222-2222-2222-222222222221"
    const val VERSION = "33333333-3333-3333-3333-333333333332"
}

/** As in `:core:sync`: `withTransaction` refuses to run on Robolectric's main looper. */
internal fun testDatabase(): CairnDatabase =
    Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        CairnDatabase::class.java,
    )
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

private val AT = Instant.parse("2026-07-01T09:30:00Z")

internal suspend fun CairnDatabase.seed() {
    studies().upsert(
        listOf(StudyEntity(id = Ids.STUDY, name = "Kestrel breeding survey", createdBy = Ids.TOMAS, createdAt = AT)),
    )
    forms().upsertForms(
        listOf(FormEntity(id = Ids.FORM, studyId = Ids.STUDY, code = "baseline_intake", createdAt = AT)),
    )
    forms().upsertVersions(
        listOf(
            FormVersionEntity(
                id = Ids.VERSION,
                formId = Ids.FORM,
                version = 3,
                schema = buildJsonObject { put("fields", JsonArray(emptyList())) },
                publishedAt = AT,
                createdAt = AT,
            ),
        ),
    )
}

internal fun submission(
    clientId: String,
    state: SyncState = SyncState.QUEUED,
    collectedBy: String = Ids.ADAKU,
) = SubmissionEntity(
    collectedBy = collectedBy,
    clientId = clientId,
    studyId = Ids.STUDY,
    formVersionId = Ids.VERSION,
    collectedAt = AT,
    data = buildJsonObject { put("body_mass", 268.0) },
    updatedAt = AT,
    syncState = state,
    pendingSince = AT,
)

internal class InMemoryCursors : SyncCursors {

    private val cursors = mutableMapOf<String, Pair<String, String>>()

    var cleared: Int = 0
        private set

    override suspend fun read(key: String, scope: String): String? = cursors[key]?.second

    override suspend fun write(key: String, scope: String, cursor: String) {
        cursors[key] = scope to cursor
    }

    override suspend fun clear() {
        cleared++
        cursors.clear()
    }

    fun isEmpty(): Boolean = cursors.isEmpty()
}

/**
 * Only the session half is real. Sync is tested against a fake server of its
 * own in `:core:sync`; what matters here is what a sign-out does to the device.
 */
internal class FakeRemote : RemoteDataSource {

    private val session = MutableStateFlow<SessionState>(SessionState.SignedIn(Ids.ADAKU))

    var signedOut: Boolean = false
        private set

    /** A sign-out that cannot reach the server still has to end the session here. */
    var signOutThrows: Boolean = false

    override val sessionState: Flow<SessionState> = session

    override suspend fun signIn(email: String, password: String): SignInOutcome {
        if (password != "cairn-dev-password") return SignInOutcome.Rejected("Invalid login credentials")
        session.value = SessionState.SignedIn(Ids.ADAKU)
        signedOut = false
        return SignInOutcome.Success
    }

    override suspend fun signOut() {
        if (signOutThrows) throw IllegalStateException("the revoke failed")
        signedOut = true
        session.value = SessionState.SignedOut
    }

    override suspend fun currentUserId(): String? = session.value.userId

    override suspend fun studies(since: String?, limit: Int): List<StudyDto> = emptyList()

    override suspend fun members(studyId: String, since: String?, limit: Int): List<StudyMemberDto> = emptyList()

    override suspend fun forms(studyId: String, since: String?, limit: Int): List<FormDto> = emptyList()

    override suspend fun formVersions(
        formIds: List<String>,
        since: String?,
        limit: Int,
    ): List<FormVersionDto> = emptyList()

    override suspend fun participants(studyId: String, since: String?, limit: Int): List<ParticipantDto> = emptyList()

    override suspend fun translations(
        formVersionIds: List<String>,
        since: String?,
        limit: Int,
    ): List<FormTranslationDto> = emptyList()

    override suspend fun submissions(studyId: String, since: String?, limit: Int): List<SubmissionDto> = emptyList()

    override suspend fun push(submissions: List<SubmissionDto>): List<SubmissionDto> = emptyList()

    override suspend fun lock(id: String, at: String): ReviewWriteOutcome = ReviewWriteOutcome.Unreachable

    override suspend fun setVoided(id: String, at: String?): ReviewWriteOutcome = ReviewWriteOutcome.Unreachable
}
