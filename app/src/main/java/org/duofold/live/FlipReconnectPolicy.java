package org.duofold.live;

/** Bounded exponential retry schedule for a Shizuku user service that may be restarted by Android. */
final class FlipReconnectPolicy {
    private FlipReconnectPolicy() {}
    static long delayMs(int attempt) {
        int safe = Math.max(0, Math.min(4, attempt));
        return Math.min(15_000L, 1_000L << safe);
    }
}
