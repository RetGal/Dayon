package mpo.dayon.assisted.network;

import mpo.dayon.common.network.Token;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

    @Test
    void testConnect() {
        // given
        final NetworkAssistedEngineConfiguration configuration = new NetworkAssistedEngineConfiguration();
        engine.configure(configuration);

        // when
        engine.connect(new Token("?token=%s"));

        // then
        verify(listener).onConnecting(configuration.getServerName(), configuration.getServerPort());
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