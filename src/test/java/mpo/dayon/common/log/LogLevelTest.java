package mpo.dayon.common.log;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LogLevelTest {

    @Test
    void shouldHaveExpectedEnumValuesInCorrectOrder() {
        // given
        LogLevel[] values = LogLevel.values();
        // then
        assertEquals(5, values.length);
        assertEquals(LogLevel.DEBUG, values[0]);
        assertEquals(LogLevel.INFO, values[1]);
        assertEquals(LogLevel.WARN, values[2]);
        assertEquals(LogLevel.ERROR, values[3]);
        assertEquals(LogLevel.FATAL, values[4]);
    }

    @Test
    void ordinalShouldIncreaseWithSeverity() {
        // given
        // then
        assertTrue(LogLevel.DEBUG.ordinal() < LogLevel.INFO.ordinal());
        assertTrue(LogLevel.INFO.ordinal() < LogLevel.WARN.ordinal());
        assertTrue(LogLevel.WARN.ordinal() < LogLevel.ERROR.ordinal());
        assertTrue(LogLevel.ERROR.ordinal() < LogLevel.FATAL.ordinal());
    }

    @Test
    void valueOfShouldReturnCorrectEnum() {
        // when, then
        assertEquals(LogLevel.DEBUG, LogLevel.valueOf("DEBUG"));
        assertEquals(LogLevel.FATAL, LogLevel.valueOf("FATAL"));
    }
}
