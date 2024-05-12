package mpo.dayon.common.utils;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class UnitUtilitiesTest {

    private static Locale defaultLocale;

    @BeforeAll
    static void getLocale() {
        defaultLocale = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("de-CH"));
    }

    @AfterAll
    static void resetLocale() {
        Locale.setDefault(defaultLocale);
    }

    @ParameterizedTest
    @CsvSource({ "1,1.00 bit", "1000,1.00 Kbit", "10000,10.00 Kbit", "1048576,1.05 Mbit" })
    void toBitSize(double bits, String expected) {
        assertEquals(expected, UnitUtilities.toBitSize(bits).trim());
    }

    @ParameterizedTest
    @CsvSource({ "1,1.00", "1000,1000.00", "1024,1.00 K", "10000,9.77 K", "1048576,1.00 M" })
    void toByteSize(double bytes, String expected) {
        assertEquals(expected, UnitUtilities.toByteSize(bytes, true).trim());
    }

    @ParameterizedTest
    @CsvSource({ "1001,1001ms", "10001,10.00s", "601000,10m01s", "3601000,1h00m01s", "86401000,1d00h00m01s" })
    void toElapsedTime(long millis, String expected) {
        assertEquals(expected, UnitUtilities.toElapsedTime(millis));
    }

    @ParameterizedTest
    @CsvSource({ "101,101ns", "10001,10us", "10100000,10ms", "10001000000,10.00s", "601000000000,10m01s", "3601000000000,1h00m01s", "86401000000000,1d00h00m01s" })
    void toElapsedNanoTime(long nanos, String expected) {
        assertEquals(expected, UnitUtilities.toElapsedNanoTime(nanos));
    }
}