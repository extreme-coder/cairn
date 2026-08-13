package app.cairn.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A member's role within one study.
 *
 * The boolean properties below decide which controls a screen offers. They are
 * not authorization. Every rule they mirror is enforced by row-level security on
 * the server, and the sync client deliberately implements none of it: a
 * collector's pull returns only their own rows because the database filters
 * them, not because the app asked for less.
 */
@Serializable
public enum class StudyRole {
    @SerialName("pi")
    PI,

    @SerialName("coordinator")
    COORDINATOR,

    @SerialName("collector")
    COLLECTOR,

    @SerialName("viewer")
    VIEWER,
    ;

    /**
     * How the role is written wherever a person reads it.
     *
     * Here rather than in a screen for the same reason validation messages are
     * generated from the field spec: the voice guide fixes these four words, and
     * a chip on the Studies screen, a row on Members and a line on a submission
     * must not drift into `Principal investigator`, `Coord.` and `PI` — the
     * one-term-per-concept rule is only real if there is one place it is written.
     */
    public val label: String
        get() = when (this) {
            PI -> "PI"
            COORDINATOR -> "Coordinator"
            COLLECTOR -> "Collector"
            VIEWER -> "Viewer"
        }

    public val showsCollectAction: Boolean
        get() = this != VIEWER

    public val showsLockAction: Boolean
        get() = this == PI || this == COORDINATOR

    public val showsAllSubmissions: Boolean
        get() = this != COLLECTOR

    public val showsFormManagement: Boolean
        get() = this == PI || this == COORDINATOR

    public val showsMemberManagement: Boolean
        get() = this == PI
}
