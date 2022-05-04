package mpo.dayon.assistant.control;

import mpo.dayon.assistant.network.NetworkAssistantEngine;
import mpo.dayon.common.network.message.NetworkKeyControlMessage;
import mpo.dayon.common.network.message.NetworkMouseControlMessage;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

import static java.lang.Thread.sleep;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ControlEngineTest {
    private final NetworkAssistantEngine network = mock(NetworkAssistantEngine.class);
    private final ControlEngine controlEngine = new ControlEngine(network);

    @BeforeEach
    void init() {
        controlEngine.start();
    }

    @Test
    void onMouseMove() throws InterruptedException {
        // when
        controlEngine.onMouseMove(1, 1);
        sleep(100);
        // then
        verify(network).sendMouseControl(any(NetworkMouseControlMessage.class));
    }

    @Test
    void onMouseWheeled() throws InterruptedException {
        // when
        controlEngine.onMouseWheeled(1, 2, 3);
        sleep(100);
        // then
        verify(network).sendMouseControl(any(NetworkMouseControlMessage.class));
    }

    @Test
    void onKeyPressed() throws InterruptedException {
        // given
        final ArgumentCaptor<NetworkKeyControlMessage> keyMessageCaptor = ArgumentCaptor.forClass(NetworkKeyControlMessage.class);
        // when
        final int keyB = 66;
        final char charB = 'B';
        controlEngine.onKeyPressed(keyB, charB);
        sleep(100);
        // then
        verify(network).sendKeyControl(keyMessageCaptor.capture());
        assertTrue(keyMessageCaptor.getValue().isPressed());
        assertEquals(keyB, keyMessageCaptor.getValue().getKeyCode());
        assertEquals(charB, keyMessageCaptor.getValue().getKeyChar());
    }
}