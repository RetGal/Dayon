package mpo.dayon.common.network.message;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NetworkPingMessageTest {

    @Test
    void aPingMessageIsASingleByteWireMessage() {
        // given, when
        NetworkPingMessage message = new NetworkPingMessage();

        // then
        assertEquals(NetworkMessageType.PING, message.getType());
        assertEquals(1, message.getWireSize());
        assertEquals("Ping message", message.toString());
    }
}
