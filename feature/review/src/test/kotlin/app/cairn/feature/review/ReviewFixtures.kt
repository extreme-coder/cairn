package app.cairn.feature.review

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import app.cairn.core.database.CairnDatabase
import app.cairn.core.database.entity.FormEntity
import app.cairn.core.database.entity.FormVersionEntity
import app.cairn.core.database.entity.ParticipantEntity
import app.cairn.core.database.entity.StudyEntity
import app.cairn.core.database.entity.StudyMemberEntity
import app.cairn.core.database.entity.SubmissionEntity
import app.cairn.core.model.StudyRole
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.time.ZoneId
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

internal object Ids {
    const val KLUANE = "11111111-1111-1111-1111-111111111111"
    const val PEEL = "11111111-1111-1111-1111-111111111112"
    const val BASELINE = "22222222-2222-2222-2222-222222222221"
    const val TRAP = "22222222-2222-2222-2222-222222222222"
    const val FUTURE = "22222222-2222-2222-2222-222222222223"

    /** The version a submission pins. `V2` exists so "reads the pinned one" is provable. */
    const val BASELINE_V2 = "33333333-3333-3333-3333-333333333331"
    const val BASELINE_V3 = "33333333-3333-3333-3333-333333333332"
    const val TRAP_V5 = "33333333-3333-3333-3333-333333333333"
    const val FUTURE_V1 = "33333333-3333-3333-3333-333333333334"
    const val PEEL_FORM = "22222222-2222-2222-2222-222222222224"
    const val PEEL_V1 = "33333333-3333-3333-3333-333333333335"

    const val KL_0148 = "44444444-4444-4444-4444-444444444448"

    /** A collector: their own rows only, and no lock action. */
    const val ADAKU = "55555555-5555-5555-5555-555555555551"

    /** A coordinator: the whole study, and both actions. */
    const val TOMAS = "55555555-5555-5555-5555-555555555552"

    /** A viewer: the whole study, and no actions at all. */
    const val NOOR = "55555555-5555-5555-5555-555555555553"

    const val SERVER_ID = "99999999-9999-9999-9999-999999999999"
}

/** 09:00 UTC, which is 09:00 in [UTC] — the zone most tests format in. */
internal val T0: Instant = Instant.parse("2026-08-13T09:00:00Z")

internal val UTC: ZoneId = ZoneId.of("UTC")

/** Whitehorse: UTC-7 in August, which is what makes the day-boundary test mean something. */
internal val YUKON: ZoneId = ZoneId.of("America/Whitehorse")

internal fun at(minutes: Int): Instant = T0 + minutes.minutes

/**
 * v2 of the baseline form. Deliberately different from [baselineV3] in a way a
 * screen would show: the label, the unit and one option's wording all changed.
 */
internal val baselineV2: JsonObject = buildJsonObject {
    putJsonArray("fields") {
        addJsonObject {
            put("key", "body_mass")
            put("type", "number")
            put("label", "Body mass")
            put("unit", "g")
        }
        addJsonObject {
            put("key", "sex")
            put("type", "single_select")
            put("label", "Sex")
            putJsonArray("options") {
                addJsonObject {
                    put("value", "female")
                    put("label", "Female")
                }
                addJsonObject {
                    put("value", "male")
                    put("label", "Male")
                }
            }
        }
        addJsonObject {
            put("key", "parasites")
            put("type", "multi_select")
            put("label", "Ectoparasites")
            putJsonArray("options") {
                addJsonObject {
                    put("value", "fleas")
                    put("label", "Fleas")
                }
                addJsonObject {
                    put("value", "ticks")
                    put("label", "Ticks")
                }
            }
        }
        addJsonObject {
            put("key", "first_seen")
            put("type", "date")
            put("label", "Date first seen")
        }
        addJsonObject {
            put("key", "notes")
            put("type", "long_text")
            put("label", "Notes")
        }
    }
}

/** v3 relabels the same key. A submission on v2 must not be read with these words. */
internal val baselineV3: JsonObject = buildJsonObject {
    putJsonArray("fields") {
        addJsonObject {
            put("key", "body_mass")
            put("type", "number")
            put("label", "Mass at capture")
            put("unit", "kg")
        }
    }
}

/** A field type published by a newer server than this build understands. */
internal val futureSchema: JsonObject = buildJsonObject {
    putJsonArray("fields") {
        addJsonObject {
            put("key", "call_recording")
            put("type", "audio")
            put("label", "Call recording")
        }
    }
}

internal fun <T> TestScope.watching(state: StateFlow<T>): StateFlow<T> {
    backgroundScope.launch { state.collect {} }
    return state
}

internal suspend fun <T> StateFlow<T>.awaiting(predicate: (T) -> Boolean): T = first(predicate)

internal fun testDatabase(): CairnDatabase =
    Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        CairnDatabase::class.java,
    )
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.Unconfined)
        .build()

/**
 * One study with all three roles in it, three forms, and two published versions
 * of the baseline form.
 *
 * All three memberships matter: the same query has to produce a lock button for
 * Tomas, none for Noor, and no Review section at all for Adaku.
 */
internal suspend fun CairnDatabase.seedKluane() {
    studies().upsert(
        listOf(StudyEntity(Ids.KLUANE, "Kluane ground squirrel survey", Ids.TOMAS, T0)),
    )
    members().upsert(
        listOf(
            StudyMemberEntity(Ids.KLUANE, Ids.ADAKU, StudyRole.COLLECTOR, T0),
            StudyMemberEntity(Ids.KLUANE, Ids.TOMAS, StudyRole.COORDINATOR, T0),
            StudyMemberEntity(Ids.KLUANE, Ids.NOOR, StudyRole.VIEWER, T0),
        ),
    )
    forms().upsertForms(
        listOf(
            FormEntity(Ids.BASELINE, Ids.KLUANE, "baseline_intake", T0),
            FormEntity(Ids.TRAP, Ids.KLUANE, "trap_check", T0),
            FormEntity(Ids.FUTURE, Ids.KLUANE, "call_survey", T0),
        ),
    )
    forms().upsertVersions(
        listOf(
            FormVersionEntity(Ids.BASELINE_V2, Ids.BASELINE, 2, baselineV2, T0, T0),
            FormVersionEntity(Ids.BASELINE_V3, Ids.BASELINE, 3, baselineV3, T0, T0),
            FormVersionEntity(Ids.TRAP_V5, Ids.TRAP, 5, baselineV2, T0, T0),
            FormVersionEntity(Ids.FUTURE_V1, Ids.FUTURE, 1, futureSchema, T0, T0),
        ),
    )
    participants().upsert(listOf(ParticipantEntity(Ids.KL_0148, Ids.KLUANE, "KL-0148", T0)))
}

/** A second study, so every study-scoped query has to actually filter. */
internal suspend fun CairnDatabase.seedPeel() {
    studies().upsert(listOf(StudyEntity(Ids.PEEL, "Peel watershed water quality", Ids.TOMAS, T0)))
    members().upsert(listOf(StudyMemberEntity(Ids.PEEL, Ids.TOMAS, StudyRole.COORDINATOR, T0)))
    forms().upsertForms(listOf(FormEntity(Ids.PEEL_FORM, Ids.PEEL, "water_sample", T0)))
    forms().upsertVersions(
        listOf(FormVersionEntity(Ids.PEEL_V1, Ids.PEEL_FORM, 1, baselineV2, T0, T0)),
    )
}

/** Every answer the v2 schema declares, so a rendering test has one of each type. */
internal fun fullPayload(): JsonObject = buildJsonObject {
    put("body_mass", 268.0)
    put("sex", "female")
    putJsonArray("parasites") {
        add(kotlinx.serialization.json.JsonPrimitive("fleas"))
        add(kotlinx.serialization.json.JsonPrimitive("ticks"))
    }
    put("first_seen", "2026-08-11")
}

internal fun submission(
    clientId: String,
    collectedBy: String = Ids.ADAKU,
    id: String? = Ids.SERVER_ID,
    studyId: String = Ids.KLUANE,
    formVersionId: String = Ids.BASELINE_V2,
    participantId: String? = Ids.KL_0148,
    collectedAt: Instant = at(14),
    lockedAt: Instant? = null,
    deletedAt: Instant? = null,
    syncState: SyncState = SyncState.UPLOADED,
    data: JsonObject = fullPayload(),
) = SubmissionEntity(
    collectedBy = collectedBy,
    clientId = clientId,
    id = id,
    studyId = studyId,
    formVersionId = formVersionId,
    participantId = participantId,
    collectedAt = collectedAt,
    data = data,
    lockedAt = lockedAt,
    updatedAt = collectedAt,
    deletedAt = deletedAt,
    syncState = syncState,
    pendingSince = if (syncState == SyncState.UPLOADED) null else collectedAt,
)

/**
 * A server that answers review writes and nothing else.
 *
 * The pull half is unused here — `:core:sync` has a fake server of its own that
 * models paging properly. What this one models is the part review depends on:
 * the server echoes what it stored, and it can refuse or be unreachable.
 */
internal class FakeRemote : RemoteDataSource {

    var offline: Boolean = false
    var refusal: String? = null

    /** What the server will claim it stored. Defaults to echoing the request. */
    var lockedAt: String? = null
    var deletedAt: String? = null
    var updatedAt: String = "2026-08-13T10:31:02.456789+00:00"

    val calls: MutableList<String> = mutableListOf()

    override val sessionState: Flow<SessionState> = flowOf(SessionState.SignedIn(Ids.TOMAS))

    override suspend fun signIn(email: String, password: String): SignInOutcome = SignInOutcome.Success

    override suspend fun signOut(): Unit = Unit

    override suspend fun currentUserId(): String = Ids.TOMAS

    override suspend fun studies(since: String?, limit: Int): List<StudyDto> = emptyList()

    override suspend fun members(studyId: String, since: String?, limit: Int): List<StudyMemberDto> =
        emptyList()

    override suspend fun forms(studyId: String, since: String?, limit: Int): List<FormDto> = emptyList()

    override suspend fun formVersions(
        formIds: List<String>,
        since: String?,
        limit: Int,
    ): List<FormVersionDto> = emptyList()

    override suspend fun participants(
        studyId: String,
        since: String?,
        limit: Int,
    ): List<ParticipantDto> = emptyList()

    override suspend fun translations(
        formVersionIds: List<String>,
        since: String?,
        limit: Int,
    ): List<FormTranslationDto> = emptyList()

    override suspend fun submissions(
        studyId: String,
        since: String?,
        limit: Int,
    ): List<SubmissionDto> = emptyList()

    override suspend fun push(submissions: List<SubmissionDto>): List<SubmissionDto> = emptyList()

    override suspend fun lock(id: String, at: String): ReviewWriteOutcome {
        calls += "lock($id, $at)"
        lockedAt = lockedAt ?: at
        return answer()
    }

    override suspend fun setVoided(id: String, at: String?): ReviewWriteOutcome {
        calls += "setVoided($id, $at)"
        deletedAt = at
        return answer()
    }

    private fun answer(): ReviewWriteOutcome = when {
        offline -> ReviewWriteOutcome.Unreachable
        refusal != null -> ReviewWriteOutcome.Refused(refusal!!)
        else -> ReviewWriteOutcome.Applied(
            SubmissionDto(
                id = Ids.SERVER_ID,
                studyId = Ids.KLUANE,
                formVersionId = Ids.BASELINE_V2,
                participantId = Ids.KL_0148,
                collectedBy = Ids.ADAKU,
                clientId = "c-148",
                collectedAt = "2026-08-13T09:14:00+00:00",
                data = fullPayload(),
                lockedAt = lockedAt,
                updatedAt = updatedAt,
                deletedAt = deletedAt,
            ),
        )
    }
}
