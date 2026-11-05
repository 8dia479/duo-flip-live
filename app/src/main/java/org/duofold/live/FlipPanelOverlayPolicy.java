package org.duofold.live;

/** Keep the Flip destination overlay warm across the brief panel power handoff. */
public final class FlipPanelOverlayPolicy {
    private FlipPanelOverlayPolicy() {}

    public static boolean shouldRetain(boolean targetAvailable, boolean enabled,
                                       boolean consentReady, boolean effectAllowed) {
        return targetAvailable && enabled && consentReady && effectAllowed;
    }

    public static boolean shouldBeVisible(boolean displayOn, boolean interactive,
                                          boolean unlocked) {
        return displayOn && interactive && unlocked;
    }
}
