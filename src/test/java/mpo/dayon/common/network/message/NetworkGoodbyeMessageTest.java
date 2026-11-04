package mpo.dayon.common.network.message;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NetworkGoodbyeMessageTest {

    @Test
    void aGoodbyeMessageIsASingleByteWireMessage() {
        // given, when
        NetworkGoodbyeMessage message = NetworkGoodbyeMessage.unmarshall();

        // then
        assertEquals(NetworkMessageType.GOODBYE, message.getType());
        assertEquals(1, message.getWireSize());
        assertEquals("Goodbye message", message.toString());
    }
}
