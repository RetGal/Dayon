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

    private Assisted assisted;
    private LogAppender logApp;

    @BeforeEach
    void init() throws NoSuchFieldException, IllegalAccessException {
        final Field out = Log.class.getDeclaredField("out");
        out.setAccessible(true);
        assisted = new Assisted(null);
        logApp = Mockito.spy(new ConsoleAppender());
        out.set("out", logApp);
    }

    @Test
    void startWithoutConfig() {
        // given
        if (!GraphicsEnvironment.isHeadless()) {
            // when  then
            assertTrue(assisted.start("localhost", null, false));
            verify(logApp).append(LogLevel.INFO, "Assisted start");
            verify(logApp, never()).append(LogLevel.INFO, "Autoconfigured [ip:localhost][port:null]");
        }
    }

    @Test
    void startAutoconnect() {
        // given
        if (!GraphicsEnvironment.isHeadless()) {
            // when then
            assertTrue(assisted.start("localhost", "12345", true));
            verify(logApp).append(LogLevel.INFO, "Autoconfigured [ip:localhost][port:12345]");
            verify(logApp).append(LogLevel.INFO, "Connecting to [localhost:12345]...");
        }
    }

    @Test
    void startAutoconnectFalse() {
        // given
        if (!GraphicsEnvironment.isHeadless()) {
            // when then
            assertTrue(assisted.start("localhost", "23456", false));
            verify(logApp).append(LogLevel.INFO, "Autoconfigured [ip:localhost][port:23456]");
            verify(logApp, never()).append(LogLevel.INFO, "Connecting to [localhost:23456]...");
        }
    }

    @Test
    void onConnected() throws Exception {
        if (!GraphicsEnvironment.isHeadless()) {
            // given
            final Field mouseEngineField = Assisted.class.getDeclaredField("mouseEngine");
            mouseEngineField.setAccessible(true);
            
            // Start the assisted to initialize the network engine and listener
            assisted.start("localhost", "12345", false);
            
            // Get the network engine
            final Field networkEngineField = Assisted.class.getDeclaredField("networkEngine");
            networkEngineField.setAccessible(true);
            Object networkEngine = networkEngineField.get(assisted);

            // Get the listeners field from NetworkAssistedEngine
            final Field listenersField = networkEngine.getClass().getDeclaredField("listeners");
            listenersField.setAccessible(true);
            Object listeners = listenersField.get(networkEngine);

            // Get the listeners list by calling getListeners() on the Listeners object
            java.util.List<Object> listenerList = (java.util.List<Object>) listeners.getClass().getMethod("getListeners").invoke(listeners);
            
            // when - trigger onConnected on the first listener (MyNetworkAssistedEngineListener)
            assertTrue(!listenerList.isEmpty());
            Object listener = listenerList.get(0);
            listener.getClass().getMethod("onConnected", String.class).invoke(listener, "test-fingerprints");
            
            // then - verify that mouseEngine is now not null
            Object mouseEngine = mouseEngineField.get(assisted);
            assertTrue(mouseEngine != null, "MouseEngine should be created onConnected");
        }
    }
}