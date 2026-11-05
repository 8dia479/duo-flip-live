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

    @Test public void hideButDoNotDisposeOverlayWhileDisplayIsOffOrLocked() {
        assertFalse(FlipPanelOverlayPolicy.shouldBeVisible(false, true, true));
        assertFalse(FlipPanelOverlayPolicy.shouldBeVisible(true, false, true));
        assertFalse(FlipPanelOverlayPolicy.shouldBeVisible(true, true, false));
        assertTrue(FlipPanelOverlayPolicy.shouldBeVisible(true, true, true));
    }
}
