package org.duofold.live;

import org.junit.Test;
import static org.junit.Assert.*;

public class FlipGlassCurveTest {
    @Test public void innerPanelUsesBookFoldProjectionPhase() {
        assertEquals(0f, FlipGlassCurve.progress(180f, true, -1, 172f, 2f), 0.0001f);
        assertTrue(FlipGlassCurve.progress(120f, true, -1, 172f, 2f) > 0f);
        assertEquals(1f, FlipGlassCurve.progress(90f, true, -1, 172f, 2f), 0.0001f);
    }

    @Test public void coverEffectGrowsAsPhoneCloses() {
        float atOpen = FlipGlassCurve.progress(180f, false, -1, 172f, 2f);
        float atHalf = FlipGlassCurve.progress(60f, false, -1, 172f, 2f);
        float nearClosed = FlipGlassCurve.progress(10f, false, -1, 172f, 2f);
        assertEquals(0f, atOpen, 0.0001f);
        assertTrue(atHalf > atOpen);
        assertTrue(nearClosed > atHalf);
        assertEquals(0f, FlipGlassCurve.progress(0f, false, -1, 172f, 2f), 0.0001f);
    }

    @Test public void coverEffectGrowsAsPhoneOpens() {
        assertEquals(0f, FlipGlassCurve.progress(0f, false, 1, 172f, 2f), 0.0001f);
        assertTrue(FlipGlassCurve.progress(60f, false, 1, 172f, 2f) >
                   FlipGlassCurve.progress(20f, false, 1, 172f, 2f));
        assertEquals(1f, FlipGlassCurve.progress(120f, false, 1, 172f, 2f), 0.0001f);
    }

    @Test public void stationaryCoverUsesCurrentAngleFallback() {
        assertTrue(FlipGlassCurve.progress(60f, false, 0, 172f, 2f) > 0f);
    }
}
