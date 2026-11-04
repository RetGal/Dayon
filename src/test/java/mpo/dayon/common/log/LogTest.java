package mpo.dayon.common.log;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

class LogTest {

    private LogAppender originalAppender;
    private LogAppender appender;

    @BeforeEach
    void replaceTheAppender() throws Exception {
        final Field out = Log.class.getDeclaredField("out");
        out.setAccessible(true);
        originalAppender = (LogAppender) out.get(null);
        appender = mock(LogAppender.class);
        out.set(null, appender);
    }

    @AfterEach
    void restoreTheAppender() throws Exception {
        final Field out = Log.class.getDeclaredField("out");
        out.setAccessible(true);
        out.set(null, originalAppender);
    }

    @Test
    void logsTheSupportedLevels() {
        // given
        final IllegalStateException error = new IllegalStateException("snafu");

        // when
        Log.info("info");
        Log.warn("warn");
        Log.error("error");
        Log.fatal("fatal");

        Log.warn(error);
        Log.warn("warn", error);
        Log.error("error", error);
        Log.fatal("fatal", error);

        // then
        verify(appender).append(LogLevel.INFO, "info");
        verify(appender).append(LogLevel.WARN, "warn");
        verify(appender).append(LogLevel.ERROR, "error");
        verify(appender).append(LogLevel.FATAL, "fatal");
        verify(appender).append(LogLevel.WARN, "snafu", error);
        verify(appender).append(LogLevel.WARN, "warn", error);
        verify(appender).append(LogLevel.ERROR, "error", error);
        verify(appender).append(LogLevel.FATAL, "fatal", error);
    }

    @Test
    void debugIsOptional() {
        // given
        final IllegalStateException error = new IllegalStateException("snafu");

        // when, then - must not throw regardless of the debug flag
        assertDoesNotThrow(() -> Log.debug("debug"));
        assertDoesNotThrow(() -> Log.debug("debug %s", () -> "lazy"));
        assertDoesNotThrow(() -> Log.debug("debug", error));
    }
}
