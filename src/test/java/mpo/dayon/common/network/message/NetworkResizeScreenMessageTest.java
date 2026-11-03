package mpo.dayon.common.network.message;

import org.junit.jupiter.api.Test;

import java.io.*;

import static org.junit.jupiter.api.Assertions.*;

class NetworkResizeScreenMessageTest {

    @Test
    void getters() {
        // given, when
        NetworkResizeScreenMessage message = new NetworkResizeScreenMessage(1920, 1080);

        // then
        assertEquals(NetworkMessageType.RESIZE, message.getType());
        assertEquals(1920, message.getWidth());
        assertEquals(1080, message.getHeight());
        assertEquals(9, message.getWireSize());
        assertEquals("[width:1920][height:1080]", message.toString());
    }

    @Test
    void marshallUnmarshallThroughTheProtocolFrame() throws IOException {
        // given
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bytes);
        NetworkMessage.marshallMagicNumber(out);
        new NetworkResizeScreenMessage(3840, 2160).marshall(out);
        out.flush();
        ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));

        // when
        NetworkMessage.unmarshallMagicNumber(in);
        assertEquals(NetworkMessageType.RESIZE, NetworkMessage.unmarshallEnum(in, NetworkMessageType.class));
        NetworkResizeScreenMessage unmarshalled = NetworkResizeScreenMessage.unmarshall(in);

        // then
        assertEquals(3840, unmarshalled.getWidth());
        assertEquals(2160, unmarshalled.getHeight());
    }
}
