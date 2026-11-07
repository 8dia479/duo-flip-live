package org.duofold.live;

/** Screenshot-independent part of the Flip Duo glass transition. */
final class FlipFallbackCurve {
    private FlipFallbackCurve() {}
    private static float clamp(float value) { return Math.max(0f, Math.min(1f, value)); }

    static float darkening(float progress, float edge) {
        float m = clamp(progress);
        float outer = clamp((edge - .2f) / .8f);
        return clamp(Math.min(.72f, 2f * m * (float) Math.pow(outer, 1.35)));
    }

    static float sweep(float progress, float edge) {
        float m = clamp(progress);
        float band = (edge - (1f - m)) / .095f;
        return Math.min(.32f, (float) Math.exp(-band * band) * .72f * m * (1f - m));
    }

    static float scale(float progress) { return 1f - .03f * clamp(progress); }
}
