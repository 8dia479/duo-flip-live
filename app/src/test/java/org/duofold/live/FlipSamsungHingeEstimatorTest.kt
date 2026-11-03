package org.duofold.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlipSamsungHingeEstimatorTest {
    @Test fun estimatesBetweenTwoTimedReadingsFromOneSensor() {
        val estimator = FlipSamsungHingeEstimator()
        estimator.add(40f, 1_000_000_000L, FlipSensorHalParser.SOURCE_FOLDING)
        estimator.add(45f, 1_100_000_000L, FlipSensorHalParser.SOURCE_FOLDING)

        val estimate = estimator.estimate(1_150_000_000L)
        assertTrue(estimate > 45f)
        assertTrue(estimate < 60f)
    }

    @Test fun doesNotInventMotionBeforeSpeedCanBeMeasured() {
        val estimator = FlipSamsungHingeEstimator()
        estimator.add(31f, 2_000_000_000L, FlipSensorHalParser.SOURCE_FOLDING)
        assertEquals(31f, estimator.estimate(2_050_000_000L), 0f)
    }

    @Test fun holdsWhenReadingsBecomeTooOld() {
        val estimator = FlipSamsungHingeEstimator()
        estimator.add(40f, 1_000_000_000L, FlipSensorHalParser.SOURCE_FOLDING)
        estimator.add(45f, 1_100_000_000L, FlipSensorHalParser.SOURCE_FOLDING)
        val held = estimator.estimate(1_200_000_000L)
        assertEquals(held, estimator.estimate(3_000_000_000L), 0f)
    }

    @Test fun hallClosedEventResetsEstimateToZero() {
        val estimator = FlipSamsungHingeEstimator()
        estimator.add(40f, 1_000_000_000L, FlipSensorHalParser.SOURCE_FOLDING)
        estimator.add(45f, 1_100_000_000L, FlipSensorHalParser.SOURCE_FOLDING)
        estimator.add(0f, 1_200_000_000L, FlipSensorHalParser.SOURCE_SHUT)
        assertEquals(0f, estimator.estimate(1_250_000_000L), 0f)
    }

    @Test fun rejectsAnIsolatedOneFrameAngleSpike() {
        val estimator = FlipSamsungHingeEstimator()
        estimator.add(40f, 1_000_000_000L, FlipSensorHalParser.SOURCE_FOLDING)
        estimator.add(44f, 1_050_000_000L, FlipSensorHalParser.SOURCE_FOLDING)
        estimator.add(112f, 1_100_000_000L, FlipSensorHalParser.SOURCE_FOLDING)
        // The estimator may continue its bounded interpolation from the last
        // confirmed velocity, but the unconfirmed 112° spike must never appear.
        val duringSpike = estimator.estimate(1_110_000_000L)
        assertTrue(duringSpike >= 44f)
        assertTrue("isolated spike leaked into estimate: $duringSpike", duringSpike < 60f)

        estimator.add(46f, 1_150_000_000L, FlipSensorHalParser.SOURCE_FOLDING)
        val afterReturn = estimator.estimate(1_150_000_000L)
        assertTrue(afterReturn >= 46f)
        assertTrue("estimate did not recover after spike: $afterReturn", afterReturn < 60f)
    }

    @Test fun confirmsARealFastMovementOnTheFollowingSample() {
        val estimator = FlipSamsungHingeEstimator()
        estimator.add(40f, 2_000_000_000L, FlipSensorHalParser.SOURCE_FOLDING)
        estimator.add(66f, 2_050_000_000L, FlipSensorHalParser.SOURCE_FOLDING)
        assertEquals(40f, estimator.estimate(2_060_000_000L), 0f)

        estimator.add(70f, 2_100_000_000L, FlipSensorHalParser.SOURCE_FOLDING)
        val estimate = estimator.estimate(2_150_000_000L)
        assertTrue(estimate > 70f)
        assertTrue(estimate < 95f)
    }

    @Test fun ignoresOutOfOrderSamples() {
        val estimator = FlipSamsungHingeEstimator()
        estimator.add(40f, 3_000_000_000L, FlipSensorHalParser.SOURCE_FOLDING)
        estimator.add(45f, 3_100_000_000L, FlipSensorHalParser.SOURCE_FOLDING)
        estimator.add(130f, 3_050_000_000L, FlipSensorHalParser.SOURCE_FOLDING)
        assertEquals(45f, estimator.estimate(3_100_000_000L), 0f)
    }
}
