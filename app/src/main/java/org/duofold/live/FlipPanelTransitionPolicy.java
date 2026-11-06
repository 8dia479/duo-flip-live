package org.duofold.live;

/** Keeps both built-in Flip panels powered throughout the physical hinge transition. */
public final class FlipPanelTransitionPolicy {
    public static final int AUTO = 0;
    public static final int CLOSED = 1;
    public static final int DUAL = 2;

    private static final float OPEN_DUAL_AT = 1.8f;
    private static final float CLOSE_TO_COVER_AT = 0.8f;
    private static final float RELEASE_DUAL_AT = 179.5f;

    private FlipPanelTransitionPolicy() {}

    /** Returns the desired device-state mode; invalid samples never change the current mode. */
    public static int next(int current, float angle) {
        if (!Float.isFinite(angle) || angle < 0f || angle > 180f) return current;
        float value = angle;

        if (current == DUAL) {
            if (value <= CLOSE_TO_COVER_AT) return CLOSED;
            if (value >= RELEASE_DUAL_AT) return AUTO;
            return DUAL;
        }

        if (current == CLOSED) {
            if (value >= RELEASE_DUAL_AT) return AUTO;
            if (value > OPEN_DUAL_AT) return DUAL;
            return CLOSED;
        }

        if (value > OPEN_DUAL_AT && value < RELEASE_DUAL_AT) return DUAL;
        return AUTO;
    }
}