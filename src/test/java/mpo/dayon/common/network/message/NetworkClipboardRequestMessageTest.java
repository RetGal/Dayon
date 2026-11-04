package mpo.dayon.common.network.message;

import org.junit.jupiter.api.Test;

import static mpo.dayon.common.network.message.NetworkMessageTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class NetworkClipboardRequestMessageTest {

    @Test
    void getTypeShouldReturnClipboardRequest() {
        // given
        NetworkClipboardRequestMessage message = new NetworkClipboardRequestMessage();
        // when, then
        assertEquals(NetworkMessageType.CLIPBOARD_REQUEST, message.getType());
    }

    @Test
    void getWireSizeShouldReturnOne() {
        // given
        NetworkClipboardRequestMessage message = new NetworkClipboardRequestMessage();
        // when, then
        assertEquals(1, message.getWireSize());
    }

    @Test
    void toStringShouldReturnExpected() {
        // given
        NetworkClipboardRequestMessage message = new NetworkClipboardRequestMessage();
        // when, then
        assertEquals("Clipboard request", message.toString());
    }

    @Test
    void marshallUnmarshallShouldRoundTrip() {
        // given
        NetworkClipboardRequestMessage original = new NetworkClipboardRequestMessage();
        // when
        byte[] data = marshallToBytes(original::marshall);
        // then
        withNoHeaderInput(data, in ->
            assertEquals(NetworkMessageType.CLIPBOARD_REQUEST,
                    NetworkMessage.unmarshallEnum(in, NetworkMessageType.class))
        );
    }
}
