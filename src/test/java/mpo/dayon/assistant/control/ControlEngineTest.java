package mpo.dayon.assistant.control;

import mpo.dayon.assistant.network.NetworkAssistantEngine;
import mpo.dayon.common.network.message.NetworkKeyControlMessage;
import mpo.dayon.common.network.message.NetworkMouseControlMessage;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class ControlEngineTest {
    private final NetworkAssistantEngine network = mock(NetworkAssistantEngine.class);
    private final ControlEngine controlEngine = new ControlEngine(network);

    @BeforeEach
    void init() {
        controlEngine.start();
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
    void onKeyPressed() {
        // given
        final ArgumentCaptor<NetworkKeyControlMessage> keyMessageCaptor = ArgumentCaptor.forClass(NetworkKeyControlMessage.class);
        // when
        final int keyB = 66;
        final char charB = 'B';
        controlEngine.onKeyPressed(keyB, charB);
        // then
        verify(network, timeout(100).atLeastOnce()).sendKeyControl(keyMessageCaptor.capture());
        assertTrue(keyMessageCaptor.getValue().isPressed());
        assertEquals(keyB, keyMessageCaptor.getValue().getKeyCode());
        assertEquals(charB, keyMessageCaptor.getValue().getKeyChar());
    }

    @Test
    void onKeyReleased() {
        // given
        final ArgumentCaptor<NetworkKeyControlMessage> keyMessageCaptor = ArgumentCaptor.forClass(NetworkKeyControlMessage.class);
        // when
        final int keyC = 67;
        final char charC = 'C';
        controlEngine.onKeyReleased(keyC, charC);
        // then
        verify(network, timeout(100).atLeastOnce()).sendKeyControl(keyMessageCaptor.capture());
        assertTrue(keyMessageCaptor.getValue().isReleased());
        assertEquals(keyC, keyMessageCaptor.getValue().getKeyCode());
        assertEquals(charC, keyMessageCaptor.getValue().getKeyChar());
    }
}