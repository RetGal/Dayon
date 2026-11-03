package mpo.dayon.common.network.message;

import org.junit.jupiter.api.Test;

import java.io.*;

import static org.junit.jupiter.api.Assertions.*;

class NetworkKeyControlMessageTest {

    private static NetworkKeyControlMessage roundTrip(NetworkKeyControlMessage message) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bytes);
        NetworkMessage.marshallMagicNumber(out);
        message.marshall(out);
        out.flush();
        ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        NetworkMessage.unmarshallMagicNumber(in);
        NetworkMessage.unmarshallEnum(in, NetworkMessageType.class);
        return NetworkKeyControlMessage.unmarshall(in);
    }

    @Test
    void pressedKey() {
        // given, when
        NetworkKeyControlMessage message = new NetworkKeyControlMessage(NetworkKeyControlMessage.KeyState.PRESSED, 13, 'D');

        // then
        assertEquals(NetworkMessageType.KEY_CONTROL, message.getType());
        assertTrue(message.isPressed());
        assertFalse(message.isReleased());
        assertEquals(13, message.getKeyCode());
        assertEquals('D', message.getKeyChar());
        assertEquals(11, message.getWireSize());
        assertEquals("PRESSED [13] [D]", message.toString());
    }

    @Test
    void releasedKey() {
        // given, when
        NetworkKeyControlMessage message = new NetworkKeyControlMessage(NetworkKeyControlMessage.KeyState.RELEASED, 65, 'A');

        // then
        assertFalse(message.isPressed());
        assertTrue(message.isReleased());
        assertEquals("RELEASED [65] [A]", message.toString());
    }

    @Test
    void marshallUnmarshallPressedKey() throws IOException {
        // given, when
        NetworkKeyControlMessage unmarshalled = roundTrip(new NetworkKeyControlMessage(NetworkKeyControlMessage.KeyState.PRESSED, 68, 'D'));

        // then
        assertTrue(unmarshalled.isPressed());
        assertEquals(68, unmarshalled.getKeyCode());
        assertEquals('D', unmarshalled.getKeyChar());
    }

    @Test
    void marshallUnmarshallReleasedKey() throws IOException {
        // given, when
        NetworkKeyControlMessage unmarshalled = roundTrip(new NetworkKeyControlMessage(NetworkKeyControlMessage.KeyState.RELEASED, 68, 'D'));

        // then
        assertTrue(unmarshalled.isReleased());
        assertFalse(unmarshalled.isPressed());
        assertEquals(68, unmarshalled.getKeyCode());
        assertEquals('D', unmarshalled.getKeyChar());
    }

    @Test
    void marshallWritesTheProtocolFrame() throws IOException {
        // given
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bytes);
        NetworkKeyControlMessage message = new NetworkKeyControlMessage(NetworkKeyControlMessage.KeyState.PRESSED, 68, 'D');

        // when
        NetworkMessage.marshallMagicNumber(out);
        message.marshall(out);
        out.flush();
        ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));

        // then
        NetworkMessage.unmarshallMagicNumber(in); // must not throw
        assertEquals(NetworkMessageType.KEY_CONTROL, NetworkMessage.unmarshallEnum(in, NetworkMessageType.class));
        int info = in.readInt();
        assertNotEquals(0, info & 1);
        assertEquals(68, in.readInt());
        assertEquals('D', in.readChar());
        assertTrue(message.getWireSize() <= bytes.size(), "the actual stream carries the message plus the serialization header");
    }
}
