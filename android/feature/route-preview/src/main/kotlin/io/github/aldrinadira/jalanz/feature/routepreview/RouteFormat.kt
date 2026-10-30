package io.github.aldrinadira.jalanz.feature.routepreview

import java.util.Locale

/** Hours and whole minutes, rounded to the nearest minute (at least 1 minute). */
internal fun hoursAndMinutes(seconds: Long): Pair<Long, Long> {
    val minutes = maxOf(1L, (seconds + 30) / 60)
    return minutes / 60 to minutes % 60
}

/** "64.4 km" with one decimal; "850 m" under 1 km. Decimal point follows the user's locale. */
internal fun formatDistance(meters: Long, locale: Locale): String =
    if (meters < 1000) "$meters m" else String.format(locale, "%.1f km", meters / 1000.0)
