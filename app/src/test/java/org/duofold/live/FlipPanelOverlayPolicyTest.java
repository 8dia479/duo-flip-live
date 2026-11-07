package org.duofold.live;

import org.junit.Test;
import static org.junit.Assert.*;

public class FlipPanelOverlayPolicyTest {
    @Test public void keepOverlayAttachedWhilePanelIsTemporarilyOff() {
        assertTrue(FlipPanelOverlayPolicy.shouldRetain(true, true, true, true));
    }

    @Test public void removeOverlayWhenItsDisplayOrPermissionIsGone() {
        assertFalse(FlipPanelOverlayPolicy.shouldRetain(false, true, true, true));
        assertFalse(FlipPanelOverlayPolicy.shouldRetain(true, false, true, true));
        assertFalse(FlipPanelOverlayPolicy.shouldRetain(true, true, false, true));
        assertFalse(FlipPanelOverlayPolicy.shouldRetain(true, true, true, false));
    }

    @Test public void visibilityFollowsTargetPanelPowerNotGlobalInteractiveOrKeyguard() {
        assertFalse(FlipPanelOverlayPolicy.shouldBeVisible(false, true, true));
        assertTrue(FlipPanelOverlayPolicy.shouldBeVisible(true, false, true));
        assertTrue(FlipPanelOverlayPolicy.shouldBeVisible(true, true, false));
        assertTrue(FlipPanelOverlayPolicy.shouldBeVisible(true, true, true));
    }
    @Test public void eitherBuiltInPanelCanKeepFlipHandoffAlive() {
        assertTrue(FlipPanelOverlayPolicy.isPanelHandoff(true, true, false, true));
        assertTrue(FlipPanelOverlayPolicy.isPanelHandoff(true, false, true, true));
    }

    @Test public void realPowerOffOrNoActivePanelIsNotAFlipHandoff() {
        assertFalse(FlipPanelOverlayPolicy.isPanelHandoff(false, true, true, true));
        assertFalse(FlipPanelOverlayPolicy.isPanelHandoff(true, false, false, false));
        assertFalse(FlipPanelOverlayPolicy.isPanelHandoff(true, false, true, false));
    }

}
