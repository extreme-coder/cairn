package app.cairn

/**
 * Every route in the app, and the only place their spelling is written.
 *
 * String routes rather than the type-safe serialization overload: the graph has
 * five destinations and two arguments, and a route built by hand in one place
 * and parsed by hand in another is exactly the kind of thing an androidTest can
 * catch and a reader cannot. [study] and [capture] build them, so no caller
 * concatenates a path.
 */
internal object CairnDestinations {

    /** The Collect tab is a stack, not a screen: studies → one study → a form. */
    const val COLLECT_GRAPH = "collect"

    const val STUDIES = "studies"

    const val STUDY_PATTERN = "study/{studyId}"

    const val CAPTURE_PATTERN = "capture/{studyId}/{formId}"

    /**
     * The coordinator's two screens, and one submission.
     *
     * All three sit inside the Collect stack rather than in a bar of their own.
     * A role belongs to a study, not to a person, so which screens exist is a
     * property of where you are standing — and the study is already the step
     * above these.
     */
    const val SUBMISSIONS_PATTERN = "submissions/{studyId}"

    const val PROGRESS_PATTERN = "progress/{studyId}"

    /**
     * Keyed the way a submission is keyed everywhere else on the device. The
     * server's `id` would be shorter and would be null for a row this device
     * collected and has not yet pushed — which is a row a coordinator may well
     * be opening.
     */
    const val SUBMISSION_PATTERN = "submission/{studyId}/{collectedBy}/{clientId}"

    const val QUEUE = "queue"

    const val SETTINGS = "settings"

    const val ARG_STUDY = "studyId"

    const val ARG_FORM = "formId"

    const val ARG_COLLECTED_BY = "collectedBy"

    const val ARG_CLIENT = "clientId"

    fun study(studyId: String): String = "study/$studyId"

    fun capture(studyId: String, formId: String): String = "capture/$studyId/$formId"

    fun submissions(studyId: String): String = "submissions/$studyId"

    fun progress(studyId: String): String = "progress/$studyId"

    fun submission(studyId: String, collectedBy: String, clientId: String): String =
        "submission/$studyId/$collectedBy/$clientId"

    /**
     * Which bottom-navigation item is lit for a given route.
     *
     * Every route inside the Collect stack lights Collect, so walking into a
     * study does not leave the bar looking as though nothing is selected. An
     * unknown route returns 0 rather than -1: the bar always has exactly one
     * selection, and "none" is not a state it can draw.
     */
    fun tabOf(route: String?): Int = when (route) {
        QUEUE -> 1
        SETTINGS -> 2
        else -> 0
    }

    /**
     * The capture screen takes the whole window.
     *
     * It has its own bottom bar carrying the one primary action, and a
     * collector filling in a form should not be one mis-tap from losing the
     * `client_id` they are working under.
     *
     * The review screens keep the bar. Their destructive action is behind a
     * confirmation, so a mis-tap costs a dismissed dialog rather than an
     * observation, and a coordinator who wanders into a study should not lose
     * the way back to their own queue.
     */
    fun showsBottomBar(route: String?): Boolean = route != CAPTURE_PATTERN
}
