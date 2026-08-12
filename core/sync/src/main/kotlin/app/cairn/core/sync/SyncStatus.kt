package app.cairn.core.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether this device has finished a sync yet.
 *
 * It exists to keep one screen honest. An empty database means either "the first
 * pull has not landed" or "this collector is in no study", and those need
 * different sentences: one says wait, the other says ask your coordinator. There
 * is no way to tell them apart from the rows themselves, because both are zero
 * rows.
 *
 * In memory and reset on sign-out, like the rest of what a session leaves
 * behind. A cold start therefore says "downloading" briefly even when the device
 * already has data — which is true, because a cold start does sync.
 *
 * Static for the same reason [SyncDependencies] is: WorkManager builds the
 * worker itself and cannot be handed anything.
 */
public object SyncStatus {

    private val completed = MutableStateFlow(false)

    /** True once a run has finished without an exception. */
    public val hasCompletedOnce: StateFlow<Boolean> = completed.asStateFlow()

    /** Called by [SyncWorker] when a run finishes. Nothing else should need to. */
    public fun succeeded() {
        completed.value = true
    }

    /** Sign-out. The next user's answer to "am I in a study" is not this one's. */
    public fun reset() {
        completed.value = false
    }
}
