package app.cairn.core.sync

import app.cairn.core.database.entity.FormEntity
import app.cairn.core.database.entity.FormTranslationEntity
import app.cairn.core.database.entity.FormVersionEntity
import app.cairn.core.database.entity.ParticipantEntity
import app.cairn.core.database.entity.StudyEntity
import app.cairn.core.database.entity.StudyMemberEntity
import app.cairn.core.database.entity.SubmissionEntity
import app.cairn.core.model.SyncState
import app.cairn.core.network.FormDto
import app.cairn.core.network.FormTranslationDto
import app.cairn.core.network.FormVersionDto
import app.cairn.core.network.ParticipantDto
import app.cairn.core.network.StudyDto
import app.cairn.core.network.StudyMemberDto
import app.cairn.core.network.SubmissionDto
import kotlin.time.Instant

/**
 * The one place the wire and the disk meet.
 *
 * `:core:network` does not know Room exists and `:core:database` does not know
 * the wire exists, which is what keeps both testable in isolation. The cost is
 * that this file has to be exhaustive, so it is: every DTO the server sends has
 * a mapping here, and the only entity that maps back is the one that travels up.
 */

/**
 * Wire text to a stored timestamp.
 *
 * Postgres sends microseconds and Room stores milliseconds, so this loses the
 * last three digits — deliberately. The columns exist to order rows on a device,
 * and epoch millis sort numerically where ISO text does not
 * ([app.cairn.core.database.CairnConverters]). Nothing that needs the full
 * precision reads these: the sync cursor keeps the server's exact string and is
 * never re-rendered from an [Instant].
 */
internal fun String.toStoredInstant(): Instant =
    try {
        Instant.parse(this)
    } catch (cause: IllegalArgumentException) {
        throw SyncException.Malformed("could not read the timestamp \"$this\"", cause)
    }

internal fun String?.toStoredInstantOrNull(): Instant? = this?.toStoredInstant()

internal fun StudyDto.toEntity(): StudyEntity = StudyEntity(
    id = id,
    name = name,
    createdBy = createdBy,
    createdAt = createdAt.toStoredInstant(),
)

internal fun StudyMemberDto.toEntity(): StudyMemberEntity = StudyMemberEntity(
    studyId = studyId,
    userId = userId,
    role = role,
    addedAt = addedAt.toStoredInstant(),
)

internal fun FormDto.toEntity(): FormEntity = FormEntity(
    id = id,
    studyId = studyId,
    code = code,
    createdAt = createdAt.toStoredInstant(),
)

/**
 * [FormVersionDto.schema] is carried across verbatim.
 *
 * Decoding it here would mean a form using a field type this build has never
 * heard of could stall the pull for the whole study. Stored raw, the failure
 * moves to the one screen that tries to open that one form.
 */
internal fun FormVersionDto.toEntity(): FormVersionEntity = FormVersionEntity(
    id = id,
    formId = formId,
    version = version,
    schema = schema,
    publishedAt = publishedAt.toStoredInstantOrNull(),
    createdAt = createdAt.toStoredInstant(),
)

internal fun ParticipantDto.toEntity(): ParticipantEntity = ParticipantEntity(
    id = id,
    studyId = studyId,
    code = code,
    createdAt = createdAt.toStoredInstant(),
)

internal fun FormTranslationDto.toEntity(): FormTranslationEntity = FormTranslationEntity(
    id = id,
    formVersionId = formVersionId,
    lang = lang,
    labels = labels,
    engine = engine,
    reviewedBy = reviewedBy,
    reviewedAt = reviewedAt.toStoredInstantOrNull(),
    createdAt = createdAt.toStoredInstant(),
    updatedAt = updatedAt.toStoredInstant(),
)

/**
 * A row coming back down is, by definition, one the server has.
 *
 * So it lands [SyncState.UPLOADED] with no `pending_since`, whatever this device
 * previously thought. The caller is responsible for not handing rows here that
 * still have unsent local changes — see [SubmissionDao.pendingKeys].
 */
internal fun SubmissionDto.toEntity(): SubmissionEntity = SubmissionEntity(
    collectedBy = collectedBy,
    clientId = clientId,
    id = id,
    studyId = studyId,
    formVersionId = formVersionId,
    participantId = participantId,
    collectedAt = collectedAt.toStoredInstant(),
    data = data,
    lockedAt = lockedAt.toStoredInstantOrNull(),
    updatedAt = updatedAt?.toStoredInstant()
        ?: throw SyncException.Malformed("submission $clientId came back without an updated_at"),
    deletedAt = deletedAt.toStoredInstantOrNull(),
    syncState = SyncState.UPLOADED,
    pendingSince = null,
)

/**
 * The only mapping that goes upward.
 *
 * `updated_at` is left null on purpose and is stripped again in
 * [app.cairn.core.network.SupabaseRemoteDataSource.push]. The server owns that
 * column; it is what last-write-wins compares, and a device that could write it
 * could win every conflict for as long as its clock was wrong. `sync_state` and
 * `pending_since` have no server counterpart at all — they are queue bookkeeping
 * and must never leave the device.
 */
internal fun SubmissionEntity.toDto(): SubmissionDto = SubmissionDto(
    id = id,
    studyId = studyId,
    formVersionId = formVersionId,
    participantId = participantId,
    collectedBy = collectedBy,
    clientId = clientId,
    collectedAt = collectedAt.toString(),
    data = data,
    lockedAt = lockedAt?.toString(),
    updatedAt = null,
    deletedAt = deletedAt?.toString(),
)
