package app.cairn.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.cairn.core.database.entity.FormEntity
import app.cairn.core.database.entity.FormTranslationEntity
import app.cairn.core.database.entity.FormVersionEntity
import app.cairn.core.database.entity.ParticipantEntity
import app.cairn.core.database.entity.StudyEntity
import app.cairn.core.database.entity.StudyMemberEntity
import app.cairn.core.model.StudyRole
import kotlinx.coroutines.flow.Flow

@Dao
public interface StudyDao {

    @Query("select * from studies order by name")
    public fun observeAll(): Flow<List<StudyEntity>>

    @Query("select * from studies where id = :studyId")
    public fun observe(studyId: String): Flow<StudyEntity?>

    @Upsert
    public suspend fun upsert(studies: List<StudyEntity>)

    @Query("delete from studies where id in (:studyIds)")
    public suspend fun delete(studyIds: List<String>)
}

@Dao
public interface StudyMemberDao {

    @Query("select * from study_members where study_id = :studyId")
    public fun observeRoster(studyId: String): Flow<List<StudyMemberEntity>>

    /**
     * Drives which controls a screen offers. It is not authorization — the server
     * decides that, and this row is only a copy of what it already decided.
     */
    @Query("select role from study_members where study_id = :studyId and user_id = :userId")
    public fun observeRole(studyId: String, userId: String): Flow<StudyRole?>

    @Upsert
    public suspend fun upsert(members: List<StudyMemberEntity>)
}

@Dao
public interface FormDao {

    @Query("select * from forms where study_id = :studyId order by code")
    public fun observeForms(studyId: String): Flow<List<FormEntity>>

    @Query(
        """
        select * from form_versions
         where form_id = :formId and published_at is not null
         order by version desc
        """,
    )
    public fun observePublishedVersions(formId: String): Flow<List<FormVersionEntity>>

    /** What a collector opens when they start a new submission. */
    @Query(
        """
        select * from form_versions
         where form_id = :formId and published_at is not null
         order by version desc
         limit 1
        """,
    )
    public fun observeCurrentVersion(formId: String): Flow<FormVersionEntity?>

    /**
     * A submission pins the version it was collected under, so review reads the
     * version by id rather than asking for the current one.
     */
    @Query("select * from form_versions where id = :versionId")
    public suspend fun version(versionId: String): FormVersionEntity?

    @Upsert
    public suspend fun upsertForms(forms: List<FormEntity>)

    @Upsert
    public suspend fun upsertVersions(versions: List<FormVersionEntity>)
}

@Dao
public interface ParticipantDao {

    @Query("select * from participants where study_id = :studyId order by code")
    public fun observeAll(studyId: String): Flow<List<ParticipantEntity>>

    @Query("select * from participants where study_id = :studyId and code = :code")
    public suspend fun byCode(studyId: String, code: String): ParticipantEntity?

    @Upsert
    public suspend fun upsert(participants: List<ParticipantEntity>)
}

@Dao
public interface FormTranslationDao {

    /**
     * Reviewed rows only. An unreviewed machine draft never reaches a screen a
     * participant can see — the same gate the server enforces, restated here so a
     * coordinator's device (which *can* read drafts) does not show one.
     */
    @Query(
        """
        select * from form_translations
         where form_version_id = :versionId and lang = :lang and reviewed_at is not null
        """,
    )
    public fun observeReviewed(versionId: String, lang: String): Flow<FormTranslationEntity?>

    @Query("select * from form_translations where form_version_id = :versionId")
    public fun observeAll(versionId: String): Flow<List<FormTranslationEntity>>

    @Upsert
    public suspend fun upsert(translations: List<FormTranslationEntity>)
}
