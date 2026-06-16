package mpo.dayon.assistant.network;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @AfterEach
    void tearDown() {
        engine.cancel();
        engine = null;
        listener = null;
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
        engine.configure(new NetworkAssistantEngineConfiguration(portNumber, "", false));

        // when // then
        assertFalse(engine.selfTest(publicIp, portNumber));
    }

}