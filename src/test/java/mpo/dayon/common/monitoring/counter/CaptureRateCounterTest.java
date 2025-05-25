package mpo.dayon.common.monitoring.counter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CaptureRateCounterTest {

    @Test
    void formatRate() {
        // given
        CaptureRateCounter crc = new CaptureRateCounter(null, null);

        // when, then
        assertEquals("10 FPS", crc.formatRate(10.0));
        assertEquals("0 FPS", crc.formatRate(0.0));
        assertEquals("- FPS", crc.formatRate(null));
        assertEquals("- FPS", crc.formatRate(Double.NaN));
    }
}