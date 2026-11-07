package org.duofold.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class FlipFallbackCurveTest {
    @Test public void noEffectAtZeroProgress() {
        assertEquals(0f, FlipFallbackCurve.darkening(0f, 1f), .0001f);
        assertEquals(0f, FlipFallbackCurve.sweep(0f, 1f), .0001f);
        assertEquals(1f, FlipFallbackCurve.scale(0f), .0001f);
    }

    @Test public void glassSweepTravelsAndPeaksMidTransition() {
        assertTrue(FlipFallbackCurve.sweep(.5f, .5f) > .15f);
        assertEquals(0f, FlipFallbackCurve.sweep(1f, 0f), .0001f);
        assertTrue(FlipFallbackCurve.darkening(1f, 1f) > .7f);
        assertEquals(.97f, FlipFallbackCurve.scale(1f), .0001f);
    }

    @Test public void progressAndEdgeAreBounded() {
        assertEquals(0f, FlipFallbackCurve.darkening(-1f, 1f), .0001f);
        assertEquals(0f, FlipFallbackCurve.sweep(2f, .5f), .0001f);
        assertEquals(.97f, FlipFallbackCurve.scale(2f), .0001f);
    }
}
