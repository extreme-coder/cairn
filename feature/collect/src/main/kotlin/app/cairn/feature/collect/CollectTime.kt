package app.cairn.feature.collect

import java.time.ZoneId
import kotlin.time.Instant

/**
 * Every timestamp and derived word a collector reads, in one file.
 *
 * The implementations moved to `:core:model` when `:feature:review` arrived and
 * needed the same six phrasings. They are re-exported here rather than called
 * through their package name so this module's screens keep reading the way they
 * did, and so this file stays the answer to "where does the Queue's wording come
 * from" — it just no longer holds the only copy.
 *
 * Nothing here reads a clock: `now` and `zone` are arguments, which is what lets
 * a test assert what "yesterday" renders as without waiting a day. `minSdk` is
 * 26, which is what makes `java.time` available with no desugaring.
 */

internal fun collectedLabel(at: Instant, now: Instant, zone: ZoneId): String =
    app.cairn.core.model.collectedLabel(at, now, zone)

internal fun lastSyncedLabel(at: Instant?, now: Instant): String =
    app.cairn.core.model.lastSyncedLabel(at, now)

internal fun submissionLabel(participantCode: String?, clientId: String): String =
    app.cairn.core.model.submissionLabel(participantCode, clientId)

internal fun formTitle(code: String): String = app.cairn.core.model.formTitle(code)

internal fun plural(count: Long, noun: String): String = app.cairn.core.model.plural(count, noun)

internal fun plural(count: Int, noun: String): String = app.cairn.core.model.plural(count, noun)
