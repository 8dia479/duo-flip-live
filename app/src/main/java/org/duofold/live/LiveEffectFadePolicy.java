package org.duofold.live;

/**
 * Holds the last Flip 6 frame when the HAL stops publishing a stationary hinge angle.
 * Keep the rendered pose for two seconds, then smoothly fade the whole effect away.
 */
public final class LiveEffectFadePolicy {
    public static final long HOLD_MS = 2_000L;
    public static final long FADE_MS = 420L;

    private LiveEffectFadePolicy() {}

    public static boolean active(long ageMs) {
        return ageMs >= 0L && ageMs <= HOLD_MS + FADE_MS;
    }

    public static float alpha(long ageMs) {
        if (ageMs < 0L) return 0f;
        if (ageMs <= HOLD_MS) return 1f;
        float t = Math.min(1f, (ageMs - HOLD_MS) / (float) FADE_MS);
        float eased = t * t * (3f - 2f * t);
        return 1f - eased;
    }
}
