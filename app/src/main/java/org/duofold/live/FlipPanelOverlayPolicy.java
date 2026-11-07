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
        // Keyguard and global interactive state must not hide an already-powered cover panel.
        return displayOn;
    }

    /** Accept either built-in panel as the live destination; display IDs can swap across posture changes. */
    public static boolean isPanelHandoff(boolean flip6, boolean interactive,
                                         boolean recentClosing, boolean anyBuiltInPanelOn) {
        return flip6 && anyBuiltInPanelOn && (interactive || recentClosing);
    }
}
