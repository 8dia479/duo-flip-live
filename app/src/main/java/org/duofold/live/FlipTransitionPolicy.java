package org.duofold.live;

/** Pure, preview-only transition math; thresholds are not Samsung handoff thresholds. */
public final class FlipTransitionPolicy {
    private FlipTransitionPolicy() {}
    public enum Posture { UNKNOWN, COVER, TRANSITION, INNER }
    public static boolean isValidAngle(float angle) { return Float.isFinite(angle) && angle >= 0f && angle <= 180f; }

    /** True only while a monotonic-clock sensor sample is recent enough to drive live UI. */
    public static boolean isFreshSample(long receivedAtNanos, long nowNanos, long maxAgeNanos) {
        return receivedAtNanos > 0L
                && nowNanos >= receivedAtNanos
                && maxAgeNanos >= 0L
                && nowNanos - receivedAtNanos <= maxAgeNanos;
    }
    public static float handoffProgress(float angle) {
        if (!isValidAngle(angle)) return Float.NaN;
        return smoothStep(angle, 65f, 115f);
    }
    public static Posture posture(float angle) {
        if (!isValidAngle(angle)) return Posture.UNKNOWN;
        if (angle <= 65f) return Posture.COVER;
        if (angle >= 115f) return Posture.INNER;
        return Posture.TRANSITION;
    }
    public static int sanitizeDurationMs(int durationMs) { return Math.max(120, Math.min(700, durationMs)); }
    public static float smoothStep(float value, float edge0, float edge1) {
        if (!Float.isFinite(value) || !Float.isFinite(edge0) || !Float.isFinite(edge1) || edge1 <= edge0) return Float.NaN;
        float t = Math.max(0f, Math.min(1f, (value - edge0) / (edge1 - edge0)));
        return t * t * (3f - 2f * t);
    }
}
