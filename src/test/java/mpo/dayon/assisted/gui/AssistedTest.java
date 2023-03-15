package mpo.dayon.assisted.gui;

import mpo.dayon.common.log.Log;
import mpo.dayon.common.log.LogAppender;
import mpo.dayon.common.log.LogLevel;
import mpo.dayon.common.log.console.ConsoleAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.awt.*;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AssistedTest {

    Assisted assisted;
    LogAppender logApp;

    @BeforeEach
    void init() throws NoSuchFieldException, IllegalAccessException {
        final Field out = Log.class.getDeclaredField("out");
        out.setAccessible(true);
        assisted = new Assisted();
        logApp = Mockito.spy(new ConsoleAppender());
        out.set("out", logApp);
    }

    @Test
    void startWithoutConfig() {
        // given
        if (!GraphicsEnvironment.isHeadless()) {
            // when
            boolean value = assisted.start("localhost", null, false);

            // then
            verify(logApp).append(LogLevel.INFO, "Assisted start");
            verify(logApp, never()).append(LogLevel.INFO, "Autoconfigured [ip:localhost][port:null]");
            assertTrue(value);
        }
    }

    @Test
    void startAutoconnect() {
        // given
        if (!GraphicsEnvironment.isHeadless()) {
            // when
            boolean value = assisted.start("localhost", "12345", true);

            // then
            verify(logApp).append(LogLevel.INFO, "Autoconfigured [ip:localhost][port:12345]");
            verify(logApp).append(LogLevel.INFO, "Connecting to [localhost][12345]...");
            assertTrue(value);
        }
    }

    @Test
    void startAutoconnectFalse() {
        // given
        if (!GraphicsEnvironment.isHeadless()) {
            // when
            boolean value = assisted.start("localhost", "23456", false);

            // then
            verify(logApp).append(LogLevel.INFO, "Autoconfigured [ip:localhost][port:23456]");
            verify(logApp, never()).append(LogLevel.INFO, "Connecting to [localhost][23456]...");
            assertTrue(value);
        }
    }
}