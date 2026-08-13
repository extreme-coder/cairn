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

    const val QUEUE = "queue"

    const val SETTINGS = "settings"

    const val ARG_STUDY = "studyId"

    const val ARG_FORM = "formId"

    fun study(studyId: String): String = "study/$studyId"

    fun capture(studyId: String, formId: String): String = "capture/$studyId/$formId"

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
     */
    fun showsBottomBar(route: String?): Boolean = route != CAPTURE_PATTERN
}
