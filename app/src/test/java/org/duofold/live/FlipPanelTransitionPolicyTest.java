package org.duofold.live;

import org.junit.Test;
import static org.junit.Assert.*;

public class FlipPanelTransitionPolicyTest {
    @Test public void unfoldingRequestsBothPanelsAsSoonAsHingeMoves() {
        assertEquals(FlipPanelTransitionPolicy.AUTO,
                FlipPanelTransitionPolicy.next(FlipPanelTransitionPolicy.AUTO, 0f));
        assertEquals(FlipPanelTransitionPolicy.DUAL,
                FlipPanelTransitionPolicy.next(FlipPanelTransitionPolicy.AUTO, 2f));
        assertEquals(FlipPanelTransitionPolicy.DUAL,
                FlipPanelTransitionPolicy.next(FlipPanelTransitionPolicy.DUAL, 45f));
        assertEquals(FlipPanelTransitionPolicy.DUAL,
                FlipPanelTransitionPolicy.next(FlipPanelTransitionPolicy.DUAL, 90f));
    }

    @Test public void foldingKeepsBothPanelsUntilAlmostClosed() {
        assertEquals(FlipPanelTransitionPolicy.DUAL,
                FlipPanelTransitionPolicy.next(FlipPanelTransitionPolicy.AUTO, 177f));
        assertEquals(FlipPanelTransitionPolicy.DUAL,
                FlipPanelTransitionPolicy.next(FlipPanelTransitionPolicy.DUAL, 45f));
        assertEquals(FlipPanelTransitionPolicy.DUAL,
                FlipPanelTransitionPolicy.next(FlipPanelTransitionPolicy.DUAL, 1f));
        assertEquals(FlipPanelTransitionPolicy.CLOSED,
                FlipPanelTransitionPolicy.next(FlipPanelTransitionPolicy.DUAL, .5f));
    }

    @Test public void closedPostureReturnsToDualOnOpeningAndAutoAtFlat() {
        assertEquals(FlipPanelTransitionPolicy.CLOSED,
                FlipPanelTransitionPolicy.next(FlipPanelTransitionPolicy.CLOSED, 1f));
        assertEquals(FlipPanelTransitionPolicy.DUAL,
                FlipPanelTransitionPolicy.next(FlipPanelTransitionPolicy.CLOSED, 2f));
        assertEquals(FlipPanelTransitionPolicy.AUTO,
                FlipPanelTransitionPolicy.next(FlipPanelTransitionPolicy.DUAL, 180f));
    }

    @Test public void hysteresisPreventsChatterAroundTheEndpoints() {
        assertEquals(FlipPanelTransitionPolicy.DUAL,
                FlipPanelTransitionPolicy.next(FlipPanelTransitionPolicy.DUAL, 1f));
        assertEquals(FlipPanelTransitionPolicy.CLOSED,
                FlipPanelTransitionPolicy.next(FlipPanelTransitionPolicy.DUAL, .8f));
        assertEquals(FlipPanelTransitionPolicy.CLOSED,
                FlipPanelTransitionPolicy.next(FlipPanelTransitionPolicy.CLOSED, 1.7f));
        assertEquals(FlipPanelTransitionPolicy.DUAL,
                FlipPanelTransitionPolicy.next(FlipPanelTransitionPolicy.CLOSED, 1.9f));
    }

    @Test public void invalidSamplesDoNotChangeTheCurrentState() {
        assertEquals(FlipPanelTransitionPolicy.DUAL,
                FlipPanelTransitionPolicy.next(FlipPanelTransitionPolicy.DUAL, Float.NaN));
        assertEquals(FlipPanelTransitionPolicy.CLOSED,
                FlipPanelTransitionPolicy.next(FlipPanelTransitionPolicy.CLOSED, 181f));
    }
}