package org.duofold.live;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class FlipReconnectPolicyTest {
    @Test public void retriesBackOffAndRemainBounded() {
        assertEquals(1_000L, FlipReconnectPolicy.delayMs(0));
        assertEquals(2_000L, FlipReconnectPolicy.delayMs(1));
        assertEquals(4_000L, FlipReconnectPolicy.delayMs(2));
        assertEquals(8_000L, FlipReconnectPolicy.delayMs(3));
        assertEquals(15_000L, FlipReconnectPolicy.delayMs(4));
        assertEquals(15_000L, FlipReconnectPolicy.delayMs(99));
        assertEquals(1_000L, FlipReconnectPolicy.delayMs(-1));
    }
}
