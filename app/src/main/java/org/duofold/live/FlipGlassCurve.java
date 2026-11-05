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
            float open = Float.isFinite(openThreshold) ? Math.max(120f, Math.min(178f, openThreshold)) : 172f;
            float span = Math.max(1f, open - 90f);
            return Math.max(0f, Math.min(1f, (open - value) / span));
        }
        // Reuse the original renderer's mirrored transition phase on the cover:
        // fully visible at the closed endpoint, then recedes as the phone opens.
        // The same angle curve naturally plays in reverse while closing.
        if (value <= closed) return 1f;
        float phase = (110f - value) / 110f;
        return Math.max(0f, Math.min(1f, phase));
    }
}
