package app.cairn.core.network

import app.cairn.core.model.StudyRole
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * The wire shape of the `public` schema, verified against the live database on
 * 2026-08-11 rather than inferred from the Room entities.
 *
 * **Timestamps are `String`, not `Instant`.** Postgres returns microsecond
 * precision and the sync cursor is compared by the server against the exact
 * value it sent. Parsing to `Instant` and re-rendering would round-trip through
 * millisecond precision and could skip or repeat a row at the page boundary.
 * Whoever stores these converts; the wire keeps them verbatim.
 */

@Serializable
public data class StudyDto(
    @SerialName("id") public val id: String,
    @SerialName("name") public val name: String,
    @SerialName("created_by") public val createdBy: String,
    @SerialName("created_at") public val createdAt: String,
    @SerialName("updated_at") public val updatedAt: String,
)

@Serializable
public data class StudyMemberDto(
    @SerialName("study_id") public val studyId: String,
    @SerialName("user_id") public val userId: String,
    @SerialName("role") public val role: StudyRole,
    @SerialName("added_at") public val addedAt: String,
    @SerialName("updated_at") public val updatedAt: String,
)

@Serializable
public data class FormDto(
    @SerialName("id") public val id: String,
    @SerialName("study_id") public val studyId: String,
    @SerialName("code") public val code: String,
    @SerialName("created_at") public val createdAt: String,
    @SerialName("updated_at") public val updatedAt: String,
)

/**
 * [schema] stays a raw [JsonObject] for the same reason `form_versions.schema`
 * does on the device: a pull must not fail because the server published a field
 * type this build has never heard of.
 */
@Serializable
public data class FormVersionDto(
    @SerialName("id") public val id: String,
    @SerialName("form_id") public val formId: String,
    @SerialName("version") public val version: Int,
    @SerialName("schema") public val schema: JsonObject,
    @SerialName("published_at") public val publishedAt: String? = null,
    @SerialName("created_at") public val createdAt: String,
    @SerialName("updated_at") public val updatedAt: String,
)

@Serializable
public data class ParticipantDto(
    @SerialName("id") public val id: String,
    @SerialName("study_id") public val studyId: String,
    @SerialName("code") public val code: String,
    @SerialName("created_at") public val createdAt: String,
    @SerialName("updated_at") public val updatedAt: String,
)

@Serializable
public data class FormTranslationDto(
    @SerialName("id") public val id: String,
    @SerialName("form_version_id") public val formVersionId: String,
    @SerialName("lang") public val lang: String,
    @SerialName("labels") public val labels: JsonObject,
    @SerialName("engine") public val engine: String? = null,
    @SerialName("reviewed_by") public val reviewedBy: String? = null,
    @SerialName("reviewed_at") public val reviewedAt: String? = null,
    @SerialName("created_at") public val createdAt: String,
    @SerialName("updated_at") public val updatedAt: String,
)

/**
 * The only row that travels upward.
 *
 * [id] is absent on the way up and present on the way back: the server assigns
 * it. [syncState] and [pendingSince] from the device entity are **not** here,
 * deliberately — the server has no such columns and they are queue bookkeeping
 * that must never leave the device.
 */
@Serializable
public data class SubmissionDto(
    @SerialName("id") public val id: String? = null,
    @SerialName("study_id") public val studyId: String,
    @SerialName("form_version_id") public val formVersionId: String,
    @SerialName("participant_id") public val participantId: String? = null,
    @SerialName("collected_by") public val collectedBy: String,
    @SerialName("client_id") public val clientId: String,
    @SerialName("collected_at") public val collectedAt: String,
    @SerialName("data") public val data: JsonObject,
    @SerialName("locked_at") public val lockedAt: String? = null,
    @SerialName("updated_at") public val updatedAt: String? = null,
    @SerialName("deleted_at") public val deletedAt: String? = null,
)
