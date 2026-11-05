package org.duofold.live;

/** Flip clamshell panel timing mapped independently from book-fold devices. */
public final class FlipGlassCurve {
    private FlipGlassCurve() {}

    public static float progress(float angle, boolean inner, int direction,
                                 float openThreshold, float closedThreshold) {
        if (!Float.isFinite(angle)) return 0f;
        float closed = Float.isFinite(closedThreshold)
                ? Math.max(0f, Math.min(12f, closedThreshold)) : 2f;
        float value = Math.max(0f, Math.min(180f, angle));
        if (inner) {
            if (value <= closed) return direction < 0 ? 1f : 0f;
            return DuoShadeCurve.progress(value, true, openThreshold);
        }
        // The newly active cover panel needs the closing effect to be visible
        // at the endpoint; the existing stale-angle grace then fades it away.
        if (value <= closed) return direction < 0 ? 1f : 0f;
        float phase = direction < 0 ? (110f - value) / 110f : value / 110f;
        return Math.max(0f, Math.min(1f, phase));
    }
}
