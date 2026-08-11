package app.cairn.feature.capture

import app.cairn.core.database.dao.FormDao
import app.cairn.core.database.dao.SubmissionDao
import app.cairn.core.database.entity.SubmissionEntity
import app.cairn.core.database.toFormSchema
import app.cairn.core.model.FieldError
import app.cairn.core.model.SyncState
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import java.util.UUID
import kotlin.time.Instant

public enum class UnopenableReason {
    NO_PUBLISHED_VERSION,
    SCHEMA_NOT_UNDERSTOOD,
    ;

    public fun message(): String = when (this) {
        NO_PUBLISHED_VERSION ->
            "This form has no published version yet. A coordinator publishes one before it can be filled in."
        SCHEMA_NOT_UNDERSTOOD ->
            "This form needs a newer version of Cairn. Update the app, then open the form again."
    }
}

public sealed interface FormOpening {
    public data class Ready(public val state: CaptureState) : FormOpening

    public data class Unopenable(public val reason: UnopenableReason) : FormOpening
}

public sealed interface SaveOutcome {
    public data class Queued(public val state: CaptureState) : SaveOutcome

    public data class Invalid(
        public val state: CaptureState,
        public val errors: List<FieldError>,
    ) : SaveOutcome
}

/**
 * Opens forms out of Room and puts finished submissions back into it.
 *
 * The whole of capture is offline. Nothing here knows the network exists; the
 * sync worker drains what this queues, later and separately.
 */
public class CaptureRepository(
    private val forms: FormDao,
    private val submissions: SubmissionDao,
) {

    /**
     * [clientId] is minted here, at open, not at save.
     *
     * A save can be retried after a crash or a second tap, and it has to land on
     * the same row both times. Generating the id when the form is saved would
     * make each attempt a new submission, which is exactly the duplicate the
     * `(collected_by, client_id)` primary key exists to prevent.
     */
    public suspend fun openForm(
        studyId: String,
        formId: String,
        collectedBy: String,
        openedAt: Instant,
        participantId: String? = null,
        clientId: String = UUID.randomUUID().toString(),
    ): FormOpening {
        val version = forms.observeCurrentVersion(formId).first()
            ?: return FormOpening.Unopenable(UnopenableReason.NO_PUBLISHED_VERSION)

        val schema = try {
            version.schema.toFormSchema()
        } catch (_: SerializationException) {
            return FormOpening.Unopenable(UnopenableReason.SCHEMA_NOT_UNDERSTOOD)
        }

        return FormOpening.Ready(
            CaptureState(
                clientId = clientId,
                collectedBy = collectedBy,
                studyId = studyId,
                formVersionId = version.id,
                schema = schema,
                openedAt = openedAt,
                participantId = participantId,
            ),
        )
    }

    /**
     * `collected_at` is when the form was opened, not when it was saved. The
     * observation happens in front of the animal; the typing that follows is
     * bookkeeping and should not move the timestamp.
     *
     * `updated_at` is the device clock and only holds until the first push, when
     * [SubmissionDao.markUploaded] replaces it with the server's value. Local
     * time never wins a last-write-wins comparison.
     */
    public suspend fun save(state: CaptureState, now: Instant): SaveOutcome {
        val attempted = state.attemptSave()
        val errors = attempted.errors
        if (errors.isNotEmpty()) return SaveOutcome.Invalid(attempted, errors)

        submissions.upsert(
            SubmissionEntity(
                collectedBy = attempted.collectedBy,
                clientId = attempted.clientId,
                studyId = attempted.studyId,
                formVersionId = attempted.formVersionId,
                participantId = attempted.participantId,
                collectedAt = attempted.openedAt,
                data = attempted.payload,
                updatedAt = now,
                syncState = SyncState.QUEUED,
                pendingSince = now,
            ),
        )
        return SaveOutcome.Queued(attempted)
    }
}
