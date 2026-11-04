package mpo.dayon.common.network.message;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NetworkScreenshotRequestMessageTest {

    @Test
    void aScreenshotRequestIsASingleByteWireMessage() {
        // given, when
        NetworkScreenshotRequestMessage message = new NetworkScreenshotRequestMessage();

        // then
        assertEquals(NetworkMessageType.SCREENSHOT_REQUEST, message.getType());
        assertEquals(1, message.getWireSize());
        assertEquals("Screenshot request message", message.toString());
    }
}
