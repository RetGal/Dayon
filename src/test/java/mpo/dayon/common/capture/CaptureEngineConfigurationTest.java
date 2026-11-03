package mpo.dayon.common.capture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CaptureEngineConfigurationTest {

    @Test
    void explicitConfigurationHoldsItsValues() {
        // given, when
        CaptureEngineConfiguration configuration = new CaptureEngineConfiguration(250, Gray8Bits.X_64, true);

        // then
        assertEquals(250, configuration.getCaptureTick());
        assertEquals(Gray8Bits.X_64, configuration.getCaptureQuantization());
        assertTrue(configuration.isCaptureColors());
    }

    @Test
    void equalsAndHashCode() {
        // given
        CaptureEngineConfiguration first = new CaptureEngineConfiguration(200, Gray8Bits.X_128, false);
        CaptureEngineConfiguration second = new CaptureEngineConfiguration(200, Gray8Bits.X_128, false);
        CaptureEngineConfiguration otherTick = new CaptureEngineConfiguration(300, Gray8Bits.X_128, false);
        CaptureEngineConfiguration otherQuantization = new CaptureEngineConfiguration(200, Gray8Bits.X_32, false);
        CaptureEngineConfiguration otherColors = new CaptureEngineConfiguration(200, Gray8Bits.X_128, true);

        // when, then
        assertEquals(first, first);
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, otherTick);
        assertNotEquals(first, otherQuantization);
        assertNotEquals(first, otherColors);
        assertNotEquals(first, null);
        assertNotEquals(first, new Object());
    }

    @Test
    void toStringDescribesTheConfiguration() {
        // given, when
        String asString = new CaptureEngineConfiguration(200, Gray8Bits.X_128, false).toString();

        // then
        assertEquals("[tick:200][quantization:X_128][color:false]", asString);
    }
}
