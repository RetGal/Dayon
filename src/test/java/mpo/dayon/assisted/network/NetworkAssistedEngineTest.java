package mpo.dayon.assisted.network;

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
        engine.connect();

        // then
        verify(listener).onConnecting(configuration.getServerName(), configuration.getServerPort());
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