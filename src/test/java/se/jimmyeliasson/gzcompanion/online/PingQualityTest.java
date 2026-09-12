package se.jimmyeliasson.gzcompanion.online;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Pure threshold logic - centralized so rendering and tests always agree on the boundaries. */
class PingQualityTest {

    @Test
    void goodAtAndBelowThreshold() {
        assertEquals(PingQuality.GOOD, PingQuality.fromLatencyMs(0));
        assertEquals(PingQuality.GOOD, PingQuality.fromLatencyMs(PingQuality.GOOD_MAX_MS));
    }

    @Test
    void okJustAboveGoodThresholdAndAtItsOwnThreshold() {
        assertEquals(PingQuality.OK, PingQuality.fromLatencyMs(PingQuality.GOOD_MAX_MS + 1));
        assertEquals(PingQuality.OK, PingQuality.fromLatencyMs(PingQuality.OK_MAX_MS));
    }

    @Test
    void poorAboveOkThreshold() {
        assertEquals(PingQuality.POOR, PingQuality.fromLatencyMs(PingQuality.OK_MAX_MS + 1));
        assertEquals(PingQuality.POOR, PingQuality.fromLatencyMs(5000));
    }
}
