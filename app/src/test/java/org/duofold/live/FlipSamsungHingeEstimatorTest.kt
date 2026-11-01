package org.duofold.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlipSamsungHingeEstimatorTest {
    @Test fun estimatesBetweenTwoTimedReadingsFromOneSensor() {
        val estimator = FlipSamsungHingeEstimator()
        estimator.add(40f, 1_000_000_000L, FlipSensorHalParser.SOURCE_FOLDING)
        estimator.add(45f, 1_100_000_000L, FlipSensorHalParser.SOURCE_FOLDING)

        // The raw HAL readings are 40° and 45°; only the animation estimate
        // is allowed to move between them based on the measured sample times.
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
}
