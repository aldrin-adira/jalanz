package io.github.aldrinadira.jalanz.core.toll

import java.time.Instant

/**
 * Discount events (04 §6.1): the first event whose window contains the transaction time, that does not exclude
 * the charging plaza and that has a matching scope, applies. Events do not stack.
 */
internal fun TollDataset.discountFor(
    plaza: Plaza,
    networkId: NetworkId?,
    vehicleClass: VehicleClass,
    t: Instant,
): DiscountEvent? = discountEvents.firstOrNull { event ->
    event.window.contains(t) &&
        plaza.id !in event.excludedPlazas &&
        event.scopes.any { it.matches(plaza, networkId, vehicleClass) }
}

private fun DiscountScope.matches(plaza: Plaza, networkId: NetworkId?, vehicleClass: VehicleClass): Boolean {
    val inScope = when (kind) {
        DiscountScopeKind.ALL -> true
        DiscountScopeKind.HIGHWAY -> scopeId == plaza.highwayId.value
        DiscountScopeKind.NETWORK -> scopeId == networkId?.value
        DiscountScopeKind.PLAZA -> scopeId == plaza.id.value
    }
    return inScope &&
        (classScheme == null || classScheme == plaza.classScheme) &&
        (this.vehicleClass == null || this.vehicleClass == vehicleClass)
}

/** The fare after [event], rounded to whole sen with the event's rule; never below zero. */
internal fun DiscountEvent.applyTo(base: Sen): Sen = when (val discount = amount) {
    is DiscountAmount.PercentOff -> {
        val scaled = base.value * (BASIS_POINTS - discount.basisPoints) // fare in 1/10000 sen
        Sen(
            when (rounding) {
                DiscountRounding.HALF_UP_SEN -> (scaled + BASIS_POINTS / 2) / BASIS_POINTS
                DiscountRounding.DOWN_SEN -> scaled / BASIS_POINTS
                DiscountRounding.UP_SEN -> (scaled + BASIS_POINTS - 1) / BASIS_POINTS
            },
        )
    }
    is DiscountAmount.FixedAmountOff -> maxOf(Sen.ZERO, base - discount.amount)
    DiscountAmount.Free -> Sen.ZERO
}

private const val BASIS_POINTS = 10_000L
