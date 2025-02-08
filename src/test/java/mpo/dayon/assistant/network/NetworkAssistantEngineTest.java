package mpo.dayon.assistant.network;

import mpo.dayon.common.network.Token;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class NetworkAssistantEngineTest {

    private NetworkAssistantEngine engine;
    private NetworkAssistantEngineListener listener;

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
        final NetworkAssistantEngineConfiguration configuration = new NetworkAssistantEngineConfiguration(12345, "http://localhost/");
        engine.reconfigure(configuration);

        // when
        engine.start(false, new Token("params"));

        // then
        verify(listener, timeout(2000).atLeastOnce()).onStarting(configuration.getPort(), true);
    }

    @Test
    void testCancel() {
        // given

        // when
        engine.cancel();

        // then
        verify(listener).onDisconnecting();
    }

    @Test
    void selfTestShouldFailIfPublicIpIsNull() {
        // given
        String publicIp = null;
        int portNumber = 12345;
        engine.configure(new NetworkAssistantEngineConfiguration(portNumber, ""));

        // when // then
        assertFalse(engine.selfTest(publicIp, portNumber));
    }

    @Test
    void selfTestShouldFailForUnreachablePort() {
        // given
        String publicIp = "1.2.3.4";
        int portNumber = 5;
        engine.configure(new NetworkAssistantEngineConfiguration(portNumber, ""));

        // when // then
        assertFalse(engine.selfTest(publicIp, portNumber));
    }

    @Test
    void selfTestShouldSucceedForReachablePort() {
        // given
        String publicIp = "127.0.0.1";
        int portNumber = 12345;
        engine.configure(new NetworkAssistantEngineConfiguration(portNumber, ""));

        // when // then
        assertTrue(engine.selfTest(publicIp, portNumber));
    }
}