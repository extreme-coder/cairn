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
