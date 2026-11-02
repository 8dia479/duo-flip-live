package org.duofold.live;

import static org.junit.Assert.*;
import org.junit.Test;

public class FlipSensorHalParserTest {
    @Test public void parsesSamsungFoldingAngleAndFractionalValues() {
        FlipSensorHalParser.Sample sample = FlipSensorHalParser.parse(
                "06-24 12:00:00.123  1000  1000 I sensors-hal: [0]folding_angle ts=123456789 ns value [ 37.5/0]");
        assertNotNull(sample);
        assertEquals(37.5f, sample.angle, 0f);
        assertEquals(123456789L, sample.timestampNanos);
        assertEquals(FlipSensorHalParser.SOURCE_FOLDING, sample.source);
    }

    @Test public void parsesHingeRecordsWithDifferentPrefixAndWhitespace() {
        FlipSensorHalParser.Sample folding = FlipSensorHalParser.parse(
                "1700000000.125  1000  1000 D sensors-hal: folding_angle ts = 123456789 ns value [ 41.75 / 0 ]");
        assertNotNull(folding);
        assertEquals(41.75f, folding.angle, 0f);
        assertEquals(FlipSensorHalParser.SOURCE_FOLDING, folding.source);

        FlipSensorHalParser.Sample lid = FlipSensorHalParser.parse(
                "1700000000.125  1000  1000 V sensors-hal: [0]lid_angle_fusion ts=987654321 ns value [2 / 76.5 / 3]");
        assertNotNull(lid);
        assertEquals(76.5f, lid.angle, 0f);
        assertEquals(FlipSensorHalParser.SOURCE_LID, lid.source);
    }

    @Test public void parsesLidAngleFusionSecondVectorValue() {
        FlipSensorHalParser.Sample sample = FlipSensorHalParser.parse(
                "06-24 12:00:00.123  1000  1000 I sensors-hal: [0]lid_angle_fusion ts=987654321 ns value [2/ 82.25/3]");
        assertNotNull(sample);
        assertEquals(82.25f, sample.angle, 0f);
        assertEquals(987654321L, sample.timestampNanos);
        assertEquals(FlipSensorHalParser.SOURCE_LID, sample.source);
    }

    @Test public void closedHallTransitionIsAnEndpointOnly() {
        FlipSensorHalParser.Sample closed = FlipSensorHalParser.parse(
                "06-24 12:00:00.123  1000  1000 I sensors-hal: [0]folding_angle device_mode(0 -> 1)");
        assertNotNull(closed);
        assertEquals(0f, closed.angle, 0f);
        assertEquals(FlipSensorHalParser.SOURCE_SHUT, closed.source);
        assertNull(FlipSensorHalParser.parse(
                "06-24 12:00:00.123  1000  1000 I sensors-hal: [0]folding_angle device_mode(1 -> 0)"));
    }

    @Test public void rejectsUnrelatedMalformedAndOutOfRangeRecords() {
        assertNull(FlipSensorHalParser.parse("I sensors-hal: accelerometer value [1/2/3]"));
        assertNull(FlipSensorHalParser.parse("[0]folding_angle ts=1 ns value [181/0]"));
        assertNull(FlipSensorHalParser.parse("[0]lid_angle_fusion ts=1 ns value [2/ -0.1/3]"));
        assertNull(FlipSensorHalParser.parse(null));
        assertNull(FlipSensorHalParser.parse(""));
    }

    @Test public void parsesEpochPrefixWithoutTrustingNonEpochText() {
        assertEquals(1_750_000_123_000L,
                FlipSensorHalParser.logcatEpochMillis("1750000123.000 100 100 I sensors-hal: message"));
        assertEquals(-1L, FlipSensorHalParser.logcatEpochMillis("I sensors-hal: message"));
    }
}
