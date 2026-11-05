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
        if (value <= closed) return 0f;
        if (inner) return DuoShadeCurve.progress(value, true, openThreshold);

        // On a closing pass the cover panel becomes visible near the closed end,
        // so the effect must grow as the hinge angle falls. On opening it grows
        // as the cover gives way to the inner screen.
        float phase = direction < 0 ? (110f - value) / 110f : value / 110f;
        return Math.max(0f, Math.min(1f, phase));
    }
}
