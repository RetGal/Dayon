package mpo.dayon.assisted.utils;

import mpo.dayon.common.capture.Gray8Bits;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.awt.*;
import java.lang.reflect.Field;

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

    @Test
    @DisabledIf(value = "java.awt.GraphicsEnvironment#isHeadless")
    void getCombinedScreenBoundsHandlesNegativeOrigins() {
        // given when
        Rectangle combined = ScreenUtilities.getCombinedScreenBounds(
                new Rectangle(-1920, 0, 1920, 1080),
                new Rectangle(0, 0, 1920, 1080),
                new Rectangle(1920, 200, 2560, 1200)
        );

        // then
        assertEquals(-1920, combined.x);
        assertEquals(0, combined.y);
        assertEquals(6400, combined.width);
        assertEquals(1400, combined.height);
    }

    @Test
    @DisabledIf(value = "java.awt.GraphicsEnvironment#isHeadless")
    void toAbsoluteLocationRestoresAbsoluteDesktopCoordinates() throws ReflectiveOperationException {
        // given
        Field sharedScreenSize = ScreenUtilities.class.getDeclaredField("sharedScreenSize");
        sharedScreenSize.setAccessible(true);
        Rectangle original = (Rectangle) sharedScreenSize.get(null);
        try {
            sharedScreenSize.set(null, new Rectangle(-1920, 0, 3840, 1080));

            // when
            Point adjusted = ScreenUtilities.toRelativeLocation(new Point(0, 0));

            // then
            assertEquals(new Point(1920, 0), adjusted);
            assertEquals(new Point(0, 0), ScreenUtilities.toAbsoluteLocation(adjusted));
        } finally {
            sharedScreenSize.set(null, original);
        }
    }
}