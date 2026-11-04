package org.duofold.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FlipTransitionPolicyTest {
    @Test public void mapsClosedAndOpenEndpoints() {
        assertEquals(0f, FlipTransitionPolicy.handoffProgress(0f), 0f);
        assertEquals(1f, FlipTransitionPolicy.handoffProgress(180f), 0f);
        assertEquals(FlipTransitionPolicy.Posture.COVER, FlipTransitionPolicy.posture(0f));
        assertEquals(FlipTransitionPolicy.Posture.INNER, FlipTransitionPolicy.posture(180f));
    }

    @Test public void blendIsSmoothAndMonotonic() {
        assertEquals(0.5f, FlipTransitionPolicy.handoffProgress(90f), 0.0001f);
        float previous = 0f;
        for (int angle = 0; angle <= 180; angle++) {
            float next = FlipTransitionPolicy.handoffProgress(angle);
            assertTrue("progress must be monotonic at " + angle, next >= previous);
            previous = next;
        }
    }

    @Test public void identifiesPreviewPosturesAndRejectsInvalidAngles() {
        assertEquals(FlipTransitionPolicy.Posture.COVER, FlipTransitionPolicy.posture(65f));
        assertEquals(FlipTransitionPolicy.Posture.TRANSITION, FlipTransitionPolicy.posture(90f));
        assertEquals(FlipTransitionPolicy.Posture.INNER, FlipTransitionPolicy.posture(115f));
        assertEquals(FlipTransitionPolicy.Posture.UNKNOWN, FlipTransitionPolicy.posture(Float.NaN));
        assertFalse(FlipTransitionPolicy.isValidAngle(181f));
        assertFalse(FlipTransitionPolicy.isValidAngle(-1f));
    }

    @Test public void sensorFreshnessExpiresAndRejectsInvalidClocks() {
        assertTrue(FlipTransitionPolicy.isFreshSample(3_000_000_000L, 4_000_000_000L, 1_500_000_000L));
        assertFalse(FlipTransitionPolicy.isFreshSample(1_000_000_000L, 4_000_000_000L, 1_500_000_000L));
        assertFalse(FlipTransitionPolicy.isFreshSample(0L, 4_000_000_000L, 1_500_000_000L));
        assertFalse(FlipTransitionPolicy.isFreshSample(5_000_000_000L, 4_000_000_000L, 1_500_000_000L));
        assertFalse(FlipTransitionPolicy.isFreshSample(3_000_000_000L, 4_000_000_000L, -1L));
    }

    @Test public void clampsPreviewSmoothingDuration() {
        assertEquals(120, FlipTransitionPolicy.sanitizeDurationMs(0));
        assertEquals(280, FlipTransitionPolicy.sanitizeDurationMs(280));
        assertEquals(700, FlipTransitionPolicy.sanitizeDurationMs(900));
    }
}
