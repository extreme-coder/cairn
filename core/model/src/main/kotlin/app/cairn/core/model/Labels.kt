package app.cairn.core.model

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Instant

/**
 * The words and timestamps more than one feature has to agree on.
 *
 * Here for the same reason [StudyRole.label], [SyncState.label] and
 * [FieldError.message] are here: the voice guide fixes these phrasings, and
 * one-term-per-concept is only true if there is one place they are written. A
 * submission row in the collector's Queue and the same row on a coordinator's
 * review list must not drift into two spellings of the same fact.
 *
 * **Pure functions of `(at, now, zone)`.** Nothing here reads a clock, which is
 * what lets a test assert what "yesterday" renders as without waiting a day, and
 * it is where localisation will change one file rather than six.
 *
 * **`Locale.ENGLISH`, not `Locale.ROOT`.** The intent is the same — the app is
 * English-only until the localisation step, and a device set to another locale
 * rendering half a screen in it would be worse than one that is consistently
 * untranslated. `ROOT` does not deliver that on a device: Android's ICU has no
 * month abbreviations for the root locale and renders `MMM` as `M08`, while the
 * JVM's `ROOT` falls back to English and renders `Aug`. So every unit test
 * passed and the phone showed "12 M08 18:44" — see the wiki's gotchas. Naming
 * the language is what makes the intent true on both.
 */

private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
private val DAY_AND_TIME = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.ENGLISH)
private val DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
private val AXIS_DAY = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

/**
 * When an observation was collected, as short as it can be and still be
 * unambiguous.
 *
 * Today is a time alone, because everything else on the screen is already today.
 * Anything older says which day, because "08:52" on a row collected last week is
 * a lie the reader has no way to catch.
 */
public fun collectedLabel(at: Instant, now: Instant, zone: ZoneId): String {
    val then = at.atZone(zone)
    val today = now.atZone(zone).toLocalDate()
    return when (then.toLocalDate()) {
        today -> then.format(TIME)
        today.minusDays(1) -> "Yesterday ${then.format(TIME)}"
        else -> then.format(DAY_AND_TIME)
    }
}

/**
 * A full date and time, for the one screen that is reading a single row rather
 * than scanning a list.
 *
 * The list forms above abbreviate because the reader is comparing rows against
 * each other. Someone deciding whether to lock one submission is comparing it
 * against a field notebook, and "Yesterday 08:52" does not survive that.
 */
public fun collectedFullLabel(at: Instant, zone: ZoneId): String {
    val then = at.atZone(zone)
    return "${then.format(DAY)} · ${then.format(TIME)}"
}

/** A day on a chart's axis. No year — the caption already says the period. */
public fun axisDayLabel(day: java.time.LocalDate): String = day.format(AXIS_DAY)

/**
 * How long ago the last clean sync was.
 *
 * Quantified, per the voice guide — never "recently" or "a while ago". A device
 * that has never synced says so plainly rather than showing a dash, because
 * "never" is the answer that should send someone to look at their connection.
 */
public fun lastSyncedLabel(at: Instant?, now: Instant): String {
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
 * What a submission row is called.
 *
 * A participant code where there is one — that is what was written on the bag
 * and what the reader will look for. Where there is not, the first segment of
 * the client id in the same mono face, because an unlabelled row is worse than
 * an ugly one.
 */
public fun submissionLabel(participantCode: String?, clientId: String): String =
    participantCode ?: clientId.take(CLIENT_ID_PREFIX).uppercase()

/** Forms have no display name on the server yet, so one is derived from the code. */
public fun formTitle(code: String): String =
    code.replace('_', ' ').replaceFirstChar { it.uppercase() }

public fun plural(count: Long, noun: String): String = if (count == 1L) noun else "${noun}s"

public fun plural(count: Int, noun: String): String = plural(count.toLong(), noun)

private fun Instant.atZone(zone: ZoneId): LocalDateTime =
    LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(toEpochMilliseconds()), zone)

private const val CLIENT_ID_PREFIX = 8
