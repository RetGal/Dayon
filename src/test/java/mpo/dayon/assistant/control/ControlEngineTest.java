package mpo.dayon.assistant.control;

import mpo.dayon.assistant.network.NetworkAssistantEngine;
import mpo.dayon.common.network.message.NetworkKeyControlMessage;
import mpo.dayon.common.network.message.NetworkMouseControlMessage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class ControlEngineTest {
    private static NetworkAssistantEngine network;
    private static ControlEngine controlEngine;

    @BeforeAll
    static void init() {
        network = mock(NetworkAssistantEngine.class);
        controlEngine = new ControlEngine(network);
    }

    @Test
    void onMouseMove() {
        // when
        controlEngine.onMouseMove(1, 1);
        // then
        verify(network, timeout(100).atLeastOnce()).sendMouseControl(any(NetworkMouseControlMessage.class));
    }

    @Test
    void onMouseWheeled() {
        // when
        controlEngine.onMouseWheeled(1, 2, 3);
        // then
        verify(network, timeout(100).atLeastOnce()).sendMouseControl(any(NetworkMouseControlMessage.class));
    }

    @Test
    void onMousePressed() {
        // when
        controlEngine.onMousePressed(1, 1, 1);
        // then
        verify(network, timeout(100).atLeastOnce()).sendMouseControl(any(NetworkMouseControlMessage.class));
    }

    @Test
    void onMouseReleased() {
        // when
        controlEngine.onMouseReleased(1, 1, 2);
        // then
        verify(network, timeout(100).atLeastOnce()).sendMouseControl(any(NetworkMouseControlMessage.class));
    }

    @Test
    void keyMustBePressedBeforeReleased() {
        // given
        final int keyD = 68;
        final char charD = 'D';
        // when
        controlEngine.onKeyReleased(keyD, charD);
        // then
        verify(network, never()).sendKeyControl(any(NetworkKeyControlMessage.class));

        // when
        controlEngine.onKeyPressed(keyD, charD);
        // then
        verify(network, timeout(50).atLeastOnce()).sendKeyControl(any(NetworkKeyControlMessage.class));

        // when
        controlEngine.onKeyReleased(keyD, charD);
        // then
        verify(network, timeout(50).atLeast(2)).sendKeyControl(any(NetworkKeyControlMessage.class));
    }
}