package app.cairn.feature.collect

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Instant

/**
 * Every timestamp a collector reads, in one file.
 *
 * Pure functions taking the zone and the current time as arguments rather than
 * reading a clock, so a test can assert what "yesterday" renders as without
 * waiting a day. `minSdk` is 26, which is what makes `java.time` available with
 * no desugaring — see the localisation plan, which depends on the same thing.
 *
 * `Locale.ROOT` is deliberate for now: the app is English-only until the
 * localisation step, and a device set to another locale rendering half a screen
 * in it would be worse than one that is consistently untranslated. This file is
 * where that changes.
 */

private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
private val DAY_AND_TIME = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.ROOT)

/**
 * When an observation was collected, as short as it can be and still be
 * unambiguous.
 *
 * Today is a time alone, because everything else on the screen is already
 * today. Anything older says which day, because "08:52" on a row collected last
 * week is a lie the collector has no way to catch.
 */
internal fun collectedLabel(at: Instant, now: Instant, zone: ZoneId): String {
    val then = at.atZone(zone)
    val today = now.atZone(zone).toLocalDate()
    return when (then.toLocalDate()) {
        today -> then.format(TIME)
        today.minusDays(1) -> "Yesterday ${then.format(TIME)}"
        else -> then.format(DAY_AND_TIME)
    }
}

/**
 * How long ago the last clean sync was.
 *
 * Quantified, per the voice guide — never "recently" or "a while ago". A device
 * that has never synced says so plainly rather than showing a dash, because
 * "never" is the answer that should send someone to look at their connection.
 */
internal fun lastSyncedLabel(at: Instant?, now: Instant): String {
    if (at == null) return "Not synced on this device yet"

    val elapsed = now - at
    val minutes = elapsed.inWholeMinutes
    val hours = elapsed.inWholeHours
    val days = elapsed.inWholeDays
    return when {
        minutes < 1 -> "Less than a minute ago"
        minutes < 60 -> "$minutes ${plural(minutes, "minute")} ago"
        hours < 24 -> "$hours ${plural(hours, "hour")} ago"
        else -> "$days ${plural(days, "day")} ago"
    }
}

/**
 * What a row is called.
 *
 * A participant code where there is one — that is what a collector wrote on the
 * bag and what they will look for. Where there is not, the first segment of the
 * client id in the same mono face, because an unlabelled row in a queue of
 * unsent observations is worse than an ugly one.
 */
internal fun submissionLabel(participantCode: String?, clientId: String): String =
    participantCode ?: clientId.take(CLIENT_ID_PREFIX).uppercase()

/** Forms have no display name on the server yet, so one is derived from the code. */
internal fun formTitle(code: String): String =
    code.replace('_', ' ').replaceFirstChar { it.uppercase() }

internal fun plural(count: Long, noun: String): String = if (count == 1L) noun else "${noun}s"

internal fun plural(count: Int, noun: String): String = plural(count.toLong(), noun)

private fun Instant.atZone(zone: ZoneId): LocalDateTime =
    LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(toEpochMilliseconds()), zone)

private const val CLIENT_ID_PREFIX = 8
