package mpo.dayon.assisted.network;

import mpo.dayon.common.network.Token;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;

import java.awt.im.InputContext;
import java.io.IOException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NetworkAssistedEngineTest {

    private NetworkAssistedEngine engine;
    private NetworkAssistedEngineListener listener;

    @BeforeEach
    void init() {
        engine = new NetworkAssistedEngine(null, null, null, null, null, null);
        listener = mock(NetworkAssistedEngineListener.class);
        engine.addListener(listener);
    }

    @AfterEach
    void tearDown() {
        engine.cancel();
        engine = null;
        listener = null;
    }

    static boolean isLocaleNull() {
        return InputContext.getInstance().getLocale() == null;
    }

    @Test
    void testConnect() {
        // given
        final NetworkAssistedEngineConfiguration configuration = new NetworkAssistedEngineConfiguration();
        engine.configure(configuration);

        // when
        engine.connect(new Token("?token=%s"), 4000);

        // then
        verify(listener).onConnecting(configuration.getServerName(), configuration.getServerPort());
    }

    @Test
    void testHostNotFound() {
        // given
        final NetworkAssistedEngineConfiguration configuration = new NetworkAssistedEngineConfiguration("snafu.foobar", 12345);
        engine.configure(configuration);

        // when
        engine.connect(new Token("?token=%s"), 200);

        // then
        verify(listener).onHostNotFound(configuration.getServerName());
    }

    @Test
    @DisabledIf("isLocaleNull")
    void testRefused() {
        // given
        final NetworkAssistedEngineConfiguration configuration = new NetworkAssistedEngineConfiguration("localhost", 12345);
        engine.configure(configuration);

        // when
        engine.connect(new Token("?token=%s"), 200);

        // then
        verify(listener).onRefused(configuration.getServerName(), configuration.getServerPort());
    }

    @Test
    void testReconfigure() {
        // given
        engine.configure(new NetworkAssistedEngineConfiguration());
        final NetworkAssistedEngineConfiguration newConfiguration = new NetworkAssistedEngineConfiguration("localhost", 9999);

        // when
        engine.reconfigure(newConfiguration);

        // then
        verify(listener).onReconfigured(newConfiguration);
    }

    @Test
    void testCancel() {
        // given

        // when
        engine.cancel();

        // then
        verify(listener).onDisconnecting();
    }
}