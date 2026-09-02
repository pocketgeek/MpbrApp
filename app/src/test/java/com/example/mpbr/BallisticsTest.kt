package com.example.mpbr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for the pure-Kotlin ballistics core. No Android dependencies.
 * Run with: ./gradlew :app:testDebugUnitTest
 *
 * The M80 reference numbers pin the app's documented default behaviour (see the
 * README "Default sanity check" section, which is kept in sync with these).
 */
class BallisticsTest {

    private val parma = Ballistics.Atmosphere(
        altitudeFt = 2231.0, temperatureF = 70.0, humidityPct = 25.0
    )

    private fun m80(atm: Ballistics.Atmosphere = parma) = Ballistics.calculateMpbr(
        muzzleVelocity      = 2750.0,
        ballisticCoeff      = 0.398,
        sightHeightIn       = 2.6,
        vitalZoneDiameterIn = 6.0,
        bulletWeightGr      = 147.0,
        dragModel           = Ballistics.DragModel.G1,
        atmosphere          = atm
    )

    // ---- Documented default-scenario sanity numbers ------------------------

    @Test
    fun m80DefaultScenarioMatchesDocumentedNumbers() {
        val r = m80()
        assertEquals("near zero",     41.0,  r.nearZeroYards,         2.0)
        assertEquals("far zero",      258.0, r.farZeroYards,          3.0)
        assertEquals("MPBR",          301.0, r.mpbrYards,             3.0)
        assertEquals("max ordinate",  3.0,   r.maxOrdinateInches,     0.1)
        assertEquals("ordinate range", 151.0, r.maxOrdinateRangeYards, 5.0)
        assertEquals("bore angle",    7.0,   r.boreAngleMoa,          0.3)
    }

    @Test
    fun m80MaxOrdinateEqualsHalfVitalZone() {
        // The bore-angle bisection's whole job: peak height == vitalZone / 2.
        val r = m80()
        assertEquals(3.0, r.maxOrdinateInches, 0.05)
    }

    @Test
    fun m80MuzzleEnergyMatchesClosedForm() {
        // KE = ½mv²: 147 gr @ 2750 fps ≈ 2468 ft·lb.
        val r = m80()
        assertEquals(2468.0, r.energyAtMuzzleFtLb, 5.0)
    }

    @Test
    fun zeroBulletWeightYieldsZeroEnergy() {
        val r = Ballistics.calculateMpbr(2750.0, 0.398, 2.6, 6.0, bulletWeightGr = 0.0)
        assertEquals(0.0, r.energyAtMuzzleFtLb, 1e-9)
        assertEquals(0.0, r.energyAtNearZeroFtLb, 1e-9)
        assertTrue(r.trajectoryTable.all { it.energyFtLb == 0.0 })
    }

    // ---- Atmosphere --------------------------------------------------------

    @Test
    fun standardAtmosphereIsUnityDensityAndStandardSpeedOfSound() {
        assertEquals(1.0, Ballistics.Atmosphere.STANDARD.densityRatio(), 1e-9)
        assertEquals(1116.45, Ballistics.Atmosphere.STANDARD.speedOfSound(), 1e-6)
    }

    @Test
    fun altitudeReducesDensity() {
        val sea = Ballistics.Atmosphere(0.0, 59.0, 0.0).densityRatio()
        val mile = Ballistics.Atmosphere(5280.0, 59.0, 0.0).densityRatio()
        assertTrue("density should fall with altitude", mile < sea)
        // Temperature is held at the user-entered value (not lapsed with
        // altitude), so this is the pure ICAO pressure ratio: ~0.823 at 1 mile.
        assertEquals(0.823, mile, 0.005)
    }

    @Test
    fun humidityReducesDensitySlightly() {
        val dry   = Ballistics.Atmosphere(0.0, 95.0, 0.0).densityRatio()
        val humid = Ballistics.Atmosphere(0.0, 95.0, 100.0).densityRatio()
        assertTrue("humid air is less dense", humid < dry)
        assertTrue("but only slightly", humid > dry * 0.97)
    }

    @Test
    fun temperatureRaisesSpeedOfSound() {
        val cold = Ballistics.Atmosphere(0.0, 0.0, 0.0).speedOfSound()
        val hot  = Ballistics.Atmosphere(0.0, 100.0, 0.0).speedOfSound()
        assertTrue(hot > cold)
    }

    // ---- Drag table interpolation ------------------------------------------

    @Test
    fun dragTableExactNodesAndClamping() {
        // Node values straight from the embedded G1/G7 tables.
        assertEquals(0.2629, Ballistics.cd(Ballistics.DragModel.G1, 0.0), 1e-9)
        assertEquals(0.1530, Ballistics.cd(Ballistics.DragModel.G1, 5.0), 1e-9)
        assertEquals(0.1198, Ballistics.cd(Ballistics.DragModel.G7, 0.0), 1e-9)
        // Beyond-table Mach clamps to the last entry.
        assertEquals(0.1530, Ballistics.cd(Ballistics.DragModel.G1, 9.0), 1e-9)
    }

    @Test
    fun dragTableLinearMidpoint() {
        // G1 between M0.70 (0.2400) and M0.80 (0.2389): midpoint at M0.75.
        assertEquals(0.23945, Ballistics.cd(Ballistics.DragModel.G1, 0.75), 1e-9)
    }

    @Test
    fun transonicDragRiseIsPresent() {
        val subsonic   = Ballistics.cd(Ballistics.DragModel.G1, 0.8)
        val transonic  = Ballistics.cd(Ballistics.DragModel.G1, 1.1)
        assertTrue("drag must spike through the sound barrier", transonic > subsonic * 1.5)
    }

    // ---- Trajectory table & angular conversions ----------------------------

    /** Synthetic straight-line trajectory: height falls 1" per yard from 0. */
    private fun syntheticTraj(maxYd: Int) = (0..maxYd).map { yd ->
        Ballistics.TrajectoryPoint(
            rangeYards = yd.toDouble(), heightInches = -yd.toDouble(),
            lateralInches = yd * 0.5, velocityFps = 2000.0 - yd, timeSeconds = yd * 0.001
        )
    }

    @Test
    fun trajectoryTableSamplesExactSteps() {
        val rows = Ballistics.trajectoryTable(syntheticTraj(500), 0.0, stepYards = 100, maxYards = 500)
        assertEquals(listOf(100, 200, 300, 400, 500), rows.map { it.rangeYards })
        assertEquals(100.0, rows[0].dropInches, 1e-9)
        assertEquals(1900.0, rows[0].velocityFps, 1e-9)
    }

    @Test
    fun trajectoryTableRespectsMinYards() {
        val rows = Ballistics.trajectoryTable(syntheticTraj(500), 0.0, 100, 500, minYards = 250)
        assertEquals(listOf(300, 400, 500), rows.map { it.rangeYards })
    }

    @Test
    fun moaAndMilConversionConstants() {
        // A 1.0472" drop at 100 yd is exactly 1 MOA; 3.6" at 100 yd is 1 mil.
        val traj = listOf(
            Ballistics.TrajectoryPoint(0.0, 0.0, 0.0, 2000.0, 0.0),
            Ballistics.TrajectoryPoint(100.0, -1.0472, 3.6, 1900.0, 0.15),
            Ballistics.TrajectoryPoint(101.0, -1.0472, 3.6, 1899.0, 0.152)
        )
        val row = Ballistics.trajectoryTable(traj, 0.0, stepYards = 100, maxYards = 100).single()
        assertEquals(1.0, row.holdoverMoa, 1e-6)
        assertEquals(1.0, row.driftMil, 1e-6)
    }

    @Test
    fun trajectoryAtInterpolatesAndBoundsChecks() {
        val traj = syntheticTraj(300)
        val row = Ballistics.trajectoryAt(traj, 150, 100.0)
        assertNotNull(row)
        assertEquals(150.0, row!!.dropInches, 1e-9)
        assertNull(Ballistics.trajectoryAt(traj, 5000))
        assertNull(Ballistics.trajectoryAt(emptyList(), 100))
        assertNull(Ballistics.trajectoryAt(traj, 0))
    }

    // ---- Wind ---------------------------------------------------------------

    @Test
    fun crosswindProducesDownwindDrift() {
        val calm  = m80()
        val windy = Ballistics.calculateMpbr(
            2750.0, 0.398, 2.6, 6.0, 147.0,
            Ballistics.DragModel.G1, parma, windSpeedMph = 10.0
        )
        val calmDrift  = calm.trajectoryTable.last().driftInches
        val windyDrift = windy.trajectoryTable.last().driftInches
        assertEquals("no wind, no drift", 0.0, calmDrift, 0.05)
        assertTrue("10 mph must drift downwind at 500 yd", windyDrift > 5.0)
        // Wind shouldn't meaningfully move the zeros.
        assertEquals(calm.farZeroYards, windy.farZeroYards, 1.0)
    }

    @Test
    fun headwindIncreasesDropAndTailwindReducesIt() {
        val calm = m80()
        val head = Ballistics.calculateMpbr(
            2750.0, 0.398, 2.6, 6.0, 147.0,
            Ballistics.DragModel.G1, parma, headwindMph = 20.0
        )
        val tail = Ballistics.calculateMpbr(
            2750.0, 0.398, 2.6, 6.0, 147.0,
            Ballistics.DragModel.G1, parma, headwindMph = -20.0
        )
        // A headwind raises airspeed → more drag → the bullet falls out of the
        // vital zone sooner; a tailwind does the opposite. The effect at 20 mph
        // is small but must be strictly ordered.
        assertTrue("headwind must shorten MPBR", head.mpbrYards < calm.mpbrYards)
        assertTrue("tailwind must extend MPBR",  tail.mpbrYards > calm.mpbrYards)
        assertTrue("headwind must shorten far zero", head.farZeroYards < calm.farZeroYards)
        // More drag also means less retained velocity at the far zero.
        assertTrue("headwind must cost velocity", head.velocityAtFarZeroFps < calm.velocityAtFarZeroFps)
        // Pure headwind produces no lateral drift.
        assertEquals("headwind alone must not drift", 0.0,
            head.trajectoryTable.last().driftInches, 0.05)
    }

    @Test
    fun zeroHeadwindDefaultMatchesExplicitZero() {
        val implicit = m80()
        val explicit = Ballistics.calculateMpbr(
            2750.0, 0.398, 2.6, 6.0, 147.0,
            Ballistics.DragModel.G1, parma, headwindMph = 0.0
        )
        assertEquals(implicit.mpbrYards,    explicit.mpbrYards,    1e-9)
        assertEquals(implicit.farZeroYards, explicit.farZeroYards, 1e-9)
        assertEquals(implicit.boreAngleMoa, explicit.boreAngleMoa, 1e-9)
    }

    // ---- Input validation ---------------------------------------------------

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMuzzleVelocityBelowFloor() {
        Ballistics.calculateMpbr(399.0, 0.398, 2.6, 6.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonPositiveBc() {
        Ballistics.calculateMpbr(2750.0, 0.0, 2.6, 6.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonPositiveVitalZone() {
        Ballistics.calculateMpbr(2750.0, 0.398, 2.6, 0.0)
    }

    @Test
    fun minimumVelocityWithExtremeLowBcStillFindsZeros() {
        // Pins that the 400 fps floor makes the "no far zero" guard defensive
        // only: even a wildly low BC at the minimum legal MV still arcs back
        // through the line of sight before the simulator's velocity cutoff,
        // so every accepted input produces a usable result.
        val r = Ballistics.calculateMpbr(400.0, 0.005, 2.6, 6.0)
        assertTrue(r.farZeroYards > 0.0)
        assertTrue(r.mpbrYards > r.farZeroYards)
    }

    // ---- Preset data integrity ----------------------------------------------

    @Test
    fun ammoPresetNamesAreUnique() {
        val dupes = Ballistics.PRESETS.groupBy { it.name }.filterValues { it.size > 1 }.keys
        assertTrue("duplicate ammo preset names: $dupes", dupes.isEmpty())
    }

    @Test
    fun reticlePresetNamesAreUnique() {
        val dupes = Ballistics.RETICLE_PRESETS.groupBy { it.name }.filterValues { it.size > 1 }.keys
        assertTrue("duplicate reticle preset names: $dupes", dupes.isEmpty())
    }

    @Test
    fun ammoPresetsHaveSaneFields() {
        for (p in Ballistics.PRESETS) {
            assertTrue("${p.name}: caliber must be set", p.caliber.isNotBlank())
            assertTrue("${p.name}: MV ${p.muzzleVelocityFps} below app minimum",
                p.muzzleVelocityFps >= 400.0)
            assertTrue("${p.name}: implausible BC ${p.ballisticCoeff}",
                p.ballisticCoeff > 0.0 && p.ballisticCoeff < 1.2)
            assertTrue("${p.name}: weight must be positive", p.bulletWeightGr > 0.0)
            assertTrue("${p.name}: sight height must be ≥ 0", p.sightHeightIn >= 0.0)
            assertTrue("${p.name}: vital zone must be positive", p.vitalZoneIn > 0.0)
        }
    }

    @Test
    fun everyAmmoPresetProducesAValidResult() {
        // The whole catalog must survive calculateMpbr — no preset may ship
        // values the engine itself rejects.
        for (p in Ballistics.PRESETS) {
            val r = Ballistics.calculateMpbr(
                p.muzzleVelocityFps, p.ballisticCoeff, p.sightHeightIn,
                p.vitalZoneIn, p.bulletWeightGr, p.dragModel
            )
            assertTrue("${p.name}: far zero not found", r.farZeroYards > 0.0)
            assertTrue("${p.name}: MPBR not found", r.mpbrYards > r.farZeroYards)
        }
    }

    @Test
    fun sfpReticlesDeclareCalibratedMagnificationAndOthersDoNot() {
        for (r in Ballistics.RETICLE_PRESETS) {
            if (r.name.contains("SFP")) {
                assertTrue("${r.name}: SFP reticle needs sfpMagnification > 0",
                    r.sfpMagnification > 0.0)
            } else {
                assertEquals("${r.name}: non-SFP reticle must not set sfpMagnification",
                    0.0, r.sfpMagnification, 1e-9)
            }
            assertTrue("${r.name}: vertExtent must be positive", r.vertExtent > 0.0)
        }
    }
}
