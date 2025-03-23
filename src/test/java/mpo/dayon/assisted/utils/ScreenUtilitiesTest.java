package mpo.dayon.assisted.utils;

import mpo.dayon.common.capture.Gray8Bits;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;

class ScreenUtilitiesTest {

    @ParameterizedTest
    @CsvSource({ "X_256", "X_128", "X_64", "X_32", "X_16", "X_8", "X_4"})
    void captureGray(String quantization) {
        if (!GraphicsEnvironment.isHeadless()) {
            // given
            int expectedSize = ScreenUtilities.getSharedScreenSize().width * ScreenUtilities.getSharedScreenSize().height;

            // when
            final byte[] bytes = ScreenUtilities.captureGray(Gray8Bits.valueOf(quantization));

            // then
            assertEquals(expectedSize, bytes.length);
        }
    }

    @Test
    void captureColors() {
        if (!GraphicsEnvironment.isHeadless()) {
            // given
            int expectedSize = ScreenUtilities.getSharedScreenSize().width * ScreenUtilities.getSharedScreenSize().height * 4;

            // when
            final byte[] bytes = ScreenUtilities.captureColors();

            // then
            assertEquals(expectedSize, bytes.length);
        }
    }
}