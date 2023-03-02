package mpo.dayon.assistant.network;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class NetworkAssistantEngineTest {

    NetworkAssistantEngine engine;
    NetworkAssistantEngineListener listener;

    @BeforeEach
    void init() {
        engine = new NetworkAssistantEngine(null, null, null);
        listener = mock(NetworkAssistantEngineListener.class);
        engine.addListener(listener);
    }

    @Test
    void testReconfigureStart() {
        // given
        engine.configure(new NetworkAssistantEngineConfiguration());
        final NetworkAssistantEngineConfiguration configuration = new NetworkAssistantEngineConfiguration(12345);
        engine.reconfigure(configuration);

        // when
        engine.start();

        // then
        verify(listener, timeout(2000).atLeastOnce()).onStarting(configuration.getPort());
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