package mpo.dayon.common.capture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class Gray8BitsTest {

    @ParameterizedTest
    @CsvSource({"X_256, 256", "X_128, 128", "X_64, 64", "X_32, 32", "X_16, 16", "X_8, 8", "X_4, 4"})
    void getLevels(Gray8Bits quantization, int expectedLevels) {
        // given, when, then
        assertEquals(expectedLevels, quantization.getLevels());
    }

    @Test
    void ordinalsArePersistedSoTheyMustStayStable() {
        // the ordinal is persisted within the preferences (see the class javadoc)
        // given, when, then
        assertEquals(Gray8Bits.X_256, Gray8Bits.values()[0]);
        assertEquals(Gray8Bits.X_128, Gray8Bits.values()[1]);
        assertEquals(Gray8Bits.X_4, Gray8Bits.values()[Gray8Bits.values().length - 1]);
        assertEquals(7, Gray8Bits.values().length);
    }
}
