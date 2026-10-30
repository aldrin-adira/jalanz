package io.github.aldrinadira.jalanz.core.toll

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Toll effective dates and user-facing times are in Malaysia time (spec 00 conventions). */
val MALAYSIA_ZONE: ZoneId = ZoneId.of("Asia/Kuala_Lumpur")

fun malaysiaTime(year: Int, month: Int, day: Int, hour: Int, minute: Int): Instant =
    LocalDateTime.of(year, month, day, hour, minute).atZone(MALAYSIA_ZONE).toInstant()

/** Half-open period `[from, to)`; `to == null` means open-ended (04 §6: `*_at(…, t)` selection rule). */
data class EffectivePeriod(val from: Instant, val to: Instant?) {
    init {
        require(to == null || to.isAfter(from)) { "period must end after it starts: $from..$to" }
    }

    fun contains(t: Instant): Boolean = !t.isBefore(from) && (to == null || t.isBefore(to))
}

/** A row that is only valid during its [period]: fares, roles, system types. */
interface EffectiveDated {
    val period: EffectivePeriod
}

/** Two rows for the same key are effective at the same time. The dataset is wrong; never guess between them. */
class OverlappingPeriodsException(message: String) : IllegalStateException(message)

/** The single row effective at [t], or null if none is. */
fun <T : EffectiveDated> Iterable<T>.effectiveAt(t: Instant): T? {
    val matches = filter { it.period.contains(t) }
    if (matches.size > 1) throw OverlappingPeriodsException("${matches.size} rows effective at $t: $matches")
    return matches.firstOrNull()
}
