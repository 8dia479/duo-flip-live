package org.duofold.live;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses only Samsung's hinge-related sensors-hal records. */
public final class FlipSensorHalParser {
    public static final int SOURCE_FOLDING = 0;
    public static final int SOURCE_LID = 1;
    public static final int SOURCE_SHUT = 2;

    private static final int FLAGS = Pattern.CASE_INSENSITIVE;
    // Samsung firmware log formats differ: some builds print "value [84/0",
    // while the Flip 6 prints "value 84/0". Accept both.
    private static final Pattern DEVICE_MODE = Pattern.compile(
            "\\bfolding_angle\\s+device_mode\\(\\s*\\d+\\s*->\\s*(\\d+)\\s*\\)", FLAGS);
    private static final Pattern FOLDING = Pattern.compile(
            "\\bfolding_angle\\s+ts\\s*=\\s*(\\d+)\\s+ns\\s+value\\s*\\[?\\s*(-?\\d+(?:\\.\\d+)?)\\s*/", FLAGS);
    // lid_angle_fusion prefixes the angle with a status field, e.g. "value 2/ 87/3".
    private static final Pattern LID = Pattern.compile(
            "\\blid_angle_fusion\\s+ts\\s*=\\s*(\\d+)\\s+ns\\s+value\\s*\\[?\\s*-?\\d+(?:\\.\\d+)?\\s*/\\s*(-?\\d+(?:\\.\\d+)?)\\s*/", FLAGS);

    private FlipSensorHalParser() {}

    public static final class Sample {
        public final float angle;
        public final long timestampNanos;
        public final int source;

        Sample(float angle, long timestampNanos, int source) {
            this.angle = angle;
            this.timestampNanos = timestampNanos;
            this.source = source;
        }

        public String sourceName() {
            switch (source) {
                case SOURCE_FOLDING: return "folding_angle";
                case SOURCE_LID: return "lid_angle_fusion";
                case SOURCE_SHUT: return "Hall device_mode";
                default: return "unknown";
            }
        }
    }

    /** Returns null for unrelated log lines or malformed/out-of-range samples. */
    public static Sample parse(String line) {
        if (line == null || line.isEmpty()) return null;

        Matcher mode = DEVICE_MODE.matcher(line);
        if (mode.find()) {
            // Samsung's Hall transition 0 -> 1 is the physical fully-closed event.
            return "1".equals(mode.group(1))
                    ? new Sample(0f, 0L, SOURCE_SHUT)
                    : null;
        }

        Matcher folding = FOLDING.matcher(line);
        if (folding.find()) {
            return make(folding.group(2), folding.group(1), SOURCE_FOLDING);
        }

        Matcher lid = LID.matcher(line);
        if (lid.find()) {
            return make(lid.group(2), lid.group(1), SOURCE_LID);
        }
        return null;
    }

    private static Sample make(String valueText, String timestampText, int source) {
        try {
            float value = Float.parseFloat(valueText);
            long timestamp = Long.parseLong(timestampText);
            if (!Float.isFinite(value) || value < 0f || value > 180f || timestamp < 0L) return null;
            return new Sample(value, timestamp, source);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /** logcat -v epoch prefix, or -1 when the line has no parseable epoch prefix. */
    public static long logcatEpochMillis(String line) {
        if (line == null) return -1L;
        String trimmed = line.trim();
        int end = trimmed.indexOf(' ');
        if (end <= 0) return -1L;
        try {
            double seconds = Double.parseDouble(trimmed.substring(0, end));
            if (!Double.isFinite(seconds) || seconds <= 0d) return -1L;
            return (long) (seconds * 1000d);
        } catch (RuntimeException ignored) {
            return -1L;
        }
    }
}
