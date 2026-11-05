package org.duofold.live;

import org.junit.Test;
import static org.junit.Assert.*;

public class LiveEffectFadePolicyTest {
    @Test public void holdsStoppedPoseForTwoSeconds() {
        assertTrue(LiveEffectFadePolicy.active(0));
        assertEquals(1f, LiveEffectFadePolicy.alpha(750), 0.0001f);
        assertEquals(1f, LiveEffectFadePolicy.alpha(1_999), 0.0001f);
        assertEquals(1f, LiveEffectFadePolicy.alpha(2_000), 0.0001f);
    }

    @Test public void fadesSmoothlyAfterTwoSeconds() {
        assertEquals(1f, LiveEffectFadePolicy.alpha(2_000), 0.0001f);
        assertEquals(0.5f, LiveEffectFadePolicy.alpha(2_210), 0.001f);
        assertTrue(LiveEffectFadePolicy.alpha(2_105) > LiveEffectFadePolicy.alpha(2_315));
        assertEquals(0f, LiveEffectFadePolicy.alpha(2_420), 0.0001f);
        assertFalse(LiveEffectFadePolicy.active(2_421));
    }

    @Test public void invalidAgeIsNotRenderable() {
        assertFalse(LiveEffectFadePolicy.active(-1));
        assertEquals(0f, LiveEffectFadePolicy.alpha(-1), 0f);
    }
}
