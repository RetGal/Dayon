package mpo.dayon.common.utils;

import java.io.IOException;

/**
 * Validates numeric values received from peer.
 * <p/>
 * The upper bound is aligned with the compression settings, which accepts values between 1 and 129600.
 */
public final class ValidationUtilities {
    public static final int MIN_VALUE = 1;
    public static final int MAX_VALUE = 129_600;

    public static final int MAX_WIDTH = 15360;
    public static final int MAX_HEIGHT = 8640;

    private ValidationUtilities() {
    }

    /**
     * @return the value when it is within the supported range
     * @throws IOException when the value is outside the supported range
     */
    public static int requireRange(String name, int value) throws IOException {
        return requireRange(name, value, MIN_VALUE, MAX_VALUE);
    }

    public static int requireRange(String name, int value, int min, int max) throws IOException {
        if (value < min || value > max) {
            throw new IOException("Invalid " + name + " [" + value + "] - must be between " + min + " and " + max);
        }
        return value;
    }
}
