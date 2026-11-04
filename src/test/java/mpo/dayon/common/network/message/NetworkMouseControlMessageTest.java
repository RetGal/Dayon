package mpo.dayon.common.network.message;

import org.junit.jupiter.api.Test;

import java.io.*;

import static org.junit.jupiter.api.Assertions.*;

class NetworkMouseControlMessageTest {

    private static NetworkMouseControlMessage roundTrip(NetworkMouseControlMessage message) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bytes);
        NetworkMessage.marshallMagicNumber(out);
        message.marshall(out);
        out.flush();
        ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        NetworkMessage.unmarshallMagicNumber(in);
        NetworkMessage.unmarshallEnum(in, NetworkMessageType.class);
        return NetworkMouseControlMessage.unmarshall(in);
    }

    @Test
    void plainMoveIsNotAControl() {
        // given, when
        NetworkMouseControlMessage message = new NetworkMouseControlMessage(10, 20);

        // then
        assertEquals(NetworkMessageType.MOUSE_CONTROL, message.getType());
        assertEquals(10, message.getX());
        assertEquals(20, message.getY());
        assertFalse(message.isPressed());
        assertFalse(message.isReleased());
        assertFalse(message.isButton1());
        assertFalse(message.isButton2());
        assertFalse(message.isButton3());
        assertFalse(message.isWheel());
        assertEquals(0, message.getRotations());
        assertEquals(9, message.getWireSize());
    }

    @Test
    void pressedButton1() {
        // given, when
        NetworkMouseControlMessage message = new NetworkMouseControlMessage(1, 2, NetworkMouseControlMessage.ButtonState.PRESSED, NetworkMouseControlMessage.BUTTON1);

        // then
        assertTrue(message.isPressed());
        assertFalse(message.isReleased());
        assertTrue(message.isButton1());
        assertFalse(message.isButton2());
        assertFalse(message.isButton3());
        assertEquals("[x:1][y:2][PRESSED][BUTTON1]", message.toString());
    }

    @Test
    void releasedButton2() {
        // given, when
        NetworkMouseControlMessage message = new NetworkMouseControlMessage(1, 2, NetworkMouseControlMessage.ButtonState.RELEASED, NetworkMouseControlMessage.BUTTON2);

        // then
        assertFalse(message.isPressed());
        assertTrue(message.isReleased());
        assertFalse(message.isButton1());
        assertTrue(message.isButton2());
        assertFalse(message.isButton3());
        assertEquals("[x:1][y:2][RELEASED][BUTTON2]", message.toString());
    }

    @Test
    void pressedButton3() {
        // given, when
        NetworkMouseControlMessage message = new NetworkMouseControlMessage(1, 2, NetworkMouseControlMessage.ButtonState.PRESSED, NetworkMouseControlMessage.BUTTON3);

        // then
        assertTrue(message.isButton3());
        assertFalse(message.isButton1());
        assertFalse(message.isButton2());
    }

    @Test
    void wheelRotation() {
        // given, when
        NetworkMouseControlMessage message = new NetworkMouseControlMessage(3, 4, -2);

        // then
        assertTrue(message.isWheel());
        assertFalse(message.isPressed());
        assertFalse(message.isReleased());
        assertEquals(-2, message.getRotations());
        assertEquals(13, message.getWireSize());
        assertEquals("[x:3][y:4][WHEEL][-2]", message.toString());
    }

    @Test
    void marshallUnmarshallMove() throws IOException {
        // given, when
        NetworkMouseControlMessage unmarshalled = roundTrip(new NetworkMouseControlMessage(1440, 900));

        // then
        assertEquals(1440, unmarshalled.getX());
        assertEquals(900, unmarshalled.getY());
        assertFalse(unmarshalled.isPressed());
        assertFalse(unmarshalled.isWheel());
        assertEquals(0, unmarshalled.getRotations());
    }

    @Test
    void marshallUnmarshallButtonPress() throws IOException {
        // given
        NetworkMouseControlMessage message = new NetworkMouseControlMessage(100, 200, NetworkMouseControlMessage.ButtonState.PRESSED, NetworkMouseControlMessage.BUTTON3);

        // when
        NetworkMouseControlMessage unmarshalled = roundTrip(message);

        // then
        assertEquals(100, unmarshalled.getX());
        assertEquals(200, unmarshalled.getY());
        assertTrue(unmarshalled.isPressed());
        assertTrue(unmarshalled.isButton3());
        assertFalse(unmarshalled.isWheel());
    }

    @Test
    void marshallUnmarshallWheel() throws IOException {
        // given
        NetworkMouseControlMessage message = new NetworkMouseControlMessage(7, 8, 3);

        // when
        NetworkMouseControlMessage unmarshalled = roundTrip(message);

        // then
        assertEquals(7, unmarshalled.getX());
        assertEquals(8, unmarshalled.getY());
        assertTrue(unmarshalled.isWheel());
        assertEquals(3, unmarshalled.getRotations());
    }

    @Test
    void marshallWritesTheProtocolFrame() throws IOException {
        // given
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bytes);
        NetworkMouseControlMessage message = new NetworkMouseControlMessage(1500, 600, NetworkMouseControlMessage.ButtonState.PRESSED, NetworkMouseControlMessage.BUTTON1);

        // when
        NetworkMessage.marshallMagicNumber(out);
        message.marshall(out);
        out.flush();
        ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));

        // then
        NetworkMessage.unmarshallMagicNumber(in); // must not throw
        assertEquals(NetworkMessageType.MOUSE_CONTROL, NetworkMessage.unmarshallEnum(in, NetworkMessageType.class));
        assertEquals(1500, in.readShort());
        assertEquals(600, in.readShort());
        int info = in.readInt();
        assertNotEquals(0, info & NetworkMouseControlMessage.BUTTON1);
        assertTrue(message.getWireSize() <= bytes.size(), "the actual stream carries the message plus the serialization header");
    }
}
