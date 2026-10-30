package io.github.aldrinadira.jalanz.core.toll

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Discount rules from 04 §6.1 beyond fixtures F2a–F2d. */
class DiscountsTest {
    private val window = EffectivePeriod(malaysiaTime(2025, 12, 23, 0, 1), malaysiaTime(2025, 12, 24, 23, 59))
    private val during = malaysiaTime(2025, 12, 23, 10, 0)
    private val class1 = VehicleClass("1")

    private fun event(
        amount: DiscountAmount = DiscountAmount.PercentOff(5000),
        rounding: DiscountRounding = DiscountRounding.HALF_UP_SEN,
        scopes: List<DiscountScope> = listOf(DiscountScope(DiscountScopeKind.ALL, null, ClassSchemeId.LLM_1_5, class1)),
        excluded: Set<PlazaId> = emptySet(),
        id: String = "TEST-EVENT",
    ) = DiscountEvent(DiscountEventId(id), id, window, amount, rounding, scopes, excluded, "https://example.invalid")

    private val plaza = Plaza(
        PlazaId("TEST-P"), HighwayId("TEST-HWY"), "P", null, PlazaKind.BOOTH, ClassSchemeId.LLM_1_5,
        GeoPoint(3.0, 101.0), EffectivePeriod(malaysiaTime(2020, 1, 1, 0, 0), null),
    )

    private fun find(vararg events: DiscountEvent, network: NetworkId? = null, cls: VehicleClass = class1, t: java.time.Instant = during) =
        TollDataset(version = "t", plazas = listOf(plaza), discountEvents = events.toList()).discountFor(plaza, network, cls, t)

    @Test
    fun `percent off rounds each way as configured`() {
        assertEquals(Sen(763), event(rounding = DiscountRounding.HALF_UP_SEN).applyTo(Sen(1525)))
        assertEquals(Sen(762), event(rounding = DiscountRounding.DOWN_SEN).applyTo(Sen(1525)))
        assertEquals(Sen(763), event(rounding = DiscountRounding.UP_SEN).applyTo(Sen(1525)))
        assertEquals(Sen(762), event(rounding = DiscountRounding.HALF_UP_SEN).applyTo(Sen(1524)))
    }

    @Test
    fun `fixed amount never goes below zero, free is zero`() {
        assertEquals(Sen(200), event(amount = DiscountAmount.FixedAmountOff(Sen(300))).applyTo(Sen(500)))
        assertEquals(Sen.ZERO, event(amount = DiscountAmount.FixedAmountOff(Sen(900))).applyTo(Sen(500)))
        assertEquals(Sen.ZERO, event(amount = DiscountAmount.Free).applyTo(Sen(500)))
    }

    @Test
    fun `applies only inside its window`() {
        assertEquals("TEST-EVENT", find(event())?.id?.value)
        assertNull(find(event(), t = malaysiaTime(2025, 12, 23, 0, 0)))
        assertNull(find(event(), t = malaysiaTime(2025, 12, 24, 23, 59)))
    }

    @Test
    fun `scope must match highway, network or plaza, class scheme and class`() {
        fun scoped(kind: DiscountScopeKind, id: String?, scheme: ClassSchemeId? = null, cls: VehicleClass? = null) =
            event(scopes = listOf(DiscountScope(kind, id, scheme, cls)))

        assertEquals("TEST-EVENT", find(scoped(DiscountScopeKind.HIGHWAY, "TEST-HWY"))?.id?.value)
        assertNull(find(scoped(DiscountScopeKind.HIGHWAY, "OTHER")))
        assertEquals("TEST-EVENT", find(scoped(DiscountScopeKind.NETWORK, "TEST-NET"), network = NetworkId("TEST-NET"))?.id?.value)
        assertNull(find(scoped(DiscountScopeKind.NETWORK, "TEST-NET")))
        assertEquals("TEST-EVENT", find(scoped(DiscountScopeKind.PLAZA, "TEST-P"))?.id?.value)
        assertNull(find(scoped(DiscountScopeKind.ALL, null, scheme = ClassSchemeId("PENANG_BRIDGE"))))
        assertNull(find(event(), cls = VehicleClass("2")))
    }

    @Test
    fun `excluded plaza gets no discount`() {
        assertNull(find(event(excluded = setOf(PlazaId("TEST-P")))))
    }

    @Test
    fun `first matching event wins, events do not stack`() {
        val first = event(id = "TEST-FIRST", amount = DiscountAmount.PercentOff(1000))
        val second = event(id = "TEST-SECOND")
        assertEquals("TEST-FIRST", find(first, second)?.id?.value)
    }
}
