package io.github.aldrinadira.jalanz.core.toll.fixtures

import io.github.aldrinadira.jalanz.core.toll.TollEstimator
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Runs every fixture case (F1–F10, 04 §9) through [TollEstimator]. All of them must pass. */
@RunWith(Parameterized::class)
class TollFixturesTest(private val fixture: TollFixture, private val case: FixtureCase) {

    @Test
    fun `engine matches fixture`() {
        val estimate = TollEstimator(fixture.dataset).estimate(case.path, case.profile, case.now)
        assertEquals(case.id, case.expected, estimate.toExpectedForm())
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{1}")
        fun cases(): List<Array<Any>> =
            TollFixtures.loadAll().flatMap { fixture -> fixture.cases.map { arrayOf<Any>(fixture, it) } }
    }
}
