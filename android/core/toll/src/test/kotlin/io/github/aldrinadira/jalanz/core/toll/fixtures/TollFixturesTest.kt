package io.github.aldrinadira.jalanz.core.toll.fixtures

import io.github.aldrinadira.jalanz.core.toll.TollEstimator
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Runs every fixture case (F1–F10, 04 §9) through [TollEstimator].
 *
 * A case runs only once the work package that implements it has added its ID to [ENGINE_READY]; until then it is
 * reported as skipped, so the suite shows exactly how much of the contract the engine meets.
 */
@RunWith(Parameterized::class)
class TollFixturesTest(private val fixture: TollFixture, private val case: FixtureCase) {

    @Test
    fun `engine matches fixture`() {
        assumeTrue("${case.id} not implemented yet (WP 1.3-1.5)", case.id in ENGINE_READY)
        val estimate = TollEstimator(fixture.dataset).estimate(case.path, case.profile, case.now)
        assertEquals(case.id, case.expected, estimate.toExpectedForm())
    }

    companion object {
        /** Case IDs the engine must pass. WP 1.4 adds F1/F3/F4/F6-F9 (detection is tested directly in WP 1.3), WP 1.5 F2a-d/F5/F10. */
        val ENGINE_READY: Set<String> = emptySet()

        @JvmStatic
        @Parameterized.Parameters(name = "{1}")
        fun cases(): List<Array<Any>> =
            TollFixtures.loadAll().flatMap { fixture -> fixture.cases.map { arrayOf<Any>(fixture, it) } }
    }
}
