package app.cairn.core.database

import app.cairn.core.database.entity.FormEntity
import app.cairn.core.database.entity.FormTranslationEntity
import app.cairn.core.database.entity.FormVersionEntity
import app.cairn.core.database.entity.ParticipantEntity
import app.cairn.core.database.entity.StudyEntity
import app.cairn.core.database.entity.StudyMemberEntity
import app.cairn.core.database.entity.SubmissionEntity
import app.cairn.core.model.StudyRole
import app.cairn.core.model.SyncState
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

internal object Ids {
    const val STUDY = "11111111-1111-1111-1111-111111111111"
    const val FORM = "22222222-2222-2222-2222-222222222222"
    const val VERSION_1 = "33333333-3333-3333-3333-333333333331"
    const val VERSION_2 = "33333333-3333-3333-3333-333333333332"
    const val PARTICIPANT = "44444444-4444-4444-4444-444444444444"
    const val ADAKU = "55555555-5555-5555-5555-555555555551"
    const val TOMAS = "55555555-5555-5555-5555-555555555552"
    const val NOOR = "55555555-5555-5555-5555-555555555553"

    const val OTHER_STUDY = "aaaa1111-1111-1111-1111-111111111111"
    const val OTHER_FORM = "aaaa2222-2222-2222-2222-222222222222"
    const val OTHER_VERSION = "aaaa3333-3333-3333-3333-333333333331"
    const val OTHER_PARTICIPANT = "aaaa4444-4444-4444-4444-444444444444"
}

internal val T0: Instant = Instant.parse("2026-07-01T09:00:00Z")

internal fun at(minutes: Int): Instant = T0 + minutes.minutes

internal fun study(id: String = Ids.STUDY, name: String = "Kestrel breeding survey") =
    StudyEntity(id = id, name = name, createdBy = Ids.ADAKU, createdAt = T0)

internal fun form(
    id: String = Ids.FORM,
    studyId: String = Ids.STUDY,
    code: String = "baseline_intake",
) = FormEntity(id = id, studyId = studyId, code = code, createdAt = T0)

internal val bodyMassSchema: JsonObject = buildJsonObject {
    put("fields", buildJsonArray {
        add(
            buildJsonObject {
                put("key", "body_mass")
                put("type", "number")
                put("label", "Body mass")
                put("unit", "g")
                put("min", 90.0)
                put("max", 400.0)
                put("required", true)
            },
        )
    })
}

internal fun formVersion(
    id: String = Ids.VERSION_1,
    formId: String = Ids.FORM,
    version: Int = 1,
    publishedAt: Instant? = T0,
    schema: JsonObject = bodyMassSchema,
) = FormVersionEntity(
    id = id,
    formId = formId,
    version = version,
    schema = schema,
    publishedAt = publishedAt,
    createdAt = T0,
)

internal fun participant(
    id: String = Ids.PARTICIPANT,
    studyId: String = Ids.STUDY,
    code: String = "K-014",
) = ParticipantEntity(id = id, studyId = studyId, code = code, createdAt = T0)

internal fun submission(
    collectedBy: String = Ids.ADAKU,
    clientId: String = "aaaaaaaa-0000-0000-0000-000000000001",
    id: String? = null,
    studyId: String = Ids.STUDY,
    formVersionId: String = Ids.VERSION_1,
    participantId: String? = Ids.PARTICIPANT,
    collectedAt: Instant = at(30),
    updatedAt: Instant = at(30),
    mass: Double = 268.0,
    deletedAt: Instant? = null,
    lockedAt: Instant? = null,
    syncState: SyncState = SyncState.QUEUED,
    pendingSince: Instant? = at(30),
) = SubmissionEntity(
    collectedBy = collectedBy,
    clientId = clientId,
    id = id,
    studyId = studyId,
    formVersionId = formVersionId,
    participantId = participantId,
    collectedAt = collectedAt,
    data = buildJsonObject { put("body_mass", mass) },
    lockedAt = lockedAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    syncState = syncState,
    pendingSince = pendingSince,
)

internal fun translation(
    lang: String = "fr",
    reviewedAt: Instant? = null,
    reviewedBy: String? = null,
    engine: String? = "libretranslate",
) = FormTranslationEntity(
    id = "translation-$lang",
    formVersionId = Ids.VERSION_1,
    lang = lang,
    labels = buildJsonObject { put("body_mass", "Masse corporelle") },
    engine = engine,
    reviewedBy = reviewedBy,
    reviewedAt = reviewedAt,
    createdAt = T0,
    updatedAt = T0,
)

internal suspend fun CairnDatabase.seedReferenceData() {
    studies().upsert(listOf(study()))
    forms().upsertForms(listOf(form()))
    forms().upsertVersions(listOf(formVersion()))
    participants().upsert(listOf(participant()))
}

/**
 * A second study the signed-in user is also a member of.
 *
 * The device holds both, so every study-scoped query has to filter rather than
 * rely on the pull having brought down only one study's rows. It reuses the form
 * code `baseline_intake`, which is legal because uniqueness is per
 * `(study_id, code)` — and is itself a check that the local index is scoped the
 * same way the server's is.
 */
internal suspend fun CairnDatabase.seedSecondStudy() {
    studies().upsert(listOf(study(id = Ids.OTHER_STUDY, name = "Alpine pika transects")))
    forms().upsertForms(
        listOf(form(id = Ids.OTHER_FORM, studyId = Ids.OTHER_STUDY, code = "baseline_intake")),
    )
    forms().upsertVersions(
        listOf(formVersion(id = Ids.OTHER_VERSION, formId = Ids.OTHER_FORM)),
    )
    participants().upsert(
        listOf(
            participant(
                id = Ids.OTHER_PARTICIPANT,
                studyId = Ids.OTHER_STUDY,
                code = "P-001",
            ),
        ),
    )
}

internal fun member(
    studyId: String = Ids.STUDY,
    userId: String = Ids.ADAKU,
    role: StudyRole = StudyRole.COLLECTOR,
) = StudyMemberEntity(studyId = studyId, userId = userId, role = role, addedAt = T0)
