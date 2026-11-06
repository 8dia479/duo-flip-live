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
        // The Flip live overlay is allowed over the keyguard; only panel power and
        // device interactivity determine whether the destination can be drawn.
        return displayOn && interactive;
    }

    /** A Flip panel swap is not a real screen-off when the other built-in panel is already on. */
    public static boolean isPanelHandoff(boolean flip6, boolean interactive, boolean unlocked,
                                         boolean primaryOff, boolean secondaryOn) {
        return flip6 && interactive && primaryOff && secondaryOn;
    }
}
