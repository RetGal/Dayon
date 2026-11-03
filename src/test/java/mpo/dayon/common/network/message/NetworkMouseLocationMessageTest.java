package mpo.dayon.common.network.message;

import org.junit.jupiter.api.Test;

import java.io.*;

import static org.junit.jupiter.api.Assertions.*;

class NetworkMouseLocationMessageTest {

    @Test
    void getters() {
        // given, when
        NetworkMouseLocationMessage message = new NetworkMouseLocationMessage(1024, 768);

        // then
        assertEquals(NetworkMessageType.MOUSE_LOCATION, message.getType());
        assertEquals(1024, message.getX());
        assertEquals(768, message.getY());
        assertEquals(5, message.getWireSize());
        assertEquals("[x:1024][y:768]", message.toString());
    }

    @Test
    void marshallUnmarshallThroughTheProtocolFrame() throws IOException {
        // given
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bytes);
        NetworkMessage.marshallMagicNumber(out);
        new NetworkMouseLocationMessage(-1, 5).marshall(out);
        out.flush();
        ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));

        // when
        NetworkMessage.unmarshallMagicNumber(in);
        assertEquals(NetworkMessageType.MOUSE_LOCATION, NetworkMessage.unmarshallEnum(in, NetworkMessageType.class));
        NetworkMouseLocationMessage unmarshalled = NetworkMouseLocationMessage.unmarshall(in);

        // then
        assertEquals(-1, unmarshalled.getX());
        assertEquals(5, unmarshalled.getY());
    }
}
