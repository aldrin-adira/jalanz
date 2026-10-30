package io.github.aldrinadira.jalanz.core.toll

import kotlin.math.absoluteValue

/**
 * Money in whole sen (RM1 = 100 sen). Never use floating point for money (spec 00 conventions, 04 §1).
 * May be negative for differences between routes ("saves RM2.00"); fares themselves are checked to be >= 0.
 */
@JvmInline
value class Sen(val value: Long) : Comparable<Sen> {
    operator fun plus(other: Sen) = Sen(value + other.value)
    operator fun minus(other: Sen) = Sen(value - other.value)
    operator fun times(factor: Int) = Sen(value * factor)
    override fun compareTo(other: Sen) = value.compareTo(other.value)

    /** "RM15.25". For logs and tests; the UI formats money with the user's locale. */
    fun toRinggitString(): String {
        val sign = if (value < 0) "-" else ""
        val abs = value.absoluteValue
        return "${sign}RM${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
    }

    companion object {
        val ZERO = Sen(0)
    }
}

fun Iterable<Sen>.sum(): Sen = fold(Sen.ZERO, Sen::plus)
