package mpo.dayon.common.network.message;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class NetworkMessageTest {

    private static ObjectInputStream asInput(ByteArrayOutputStream bytes) throws IOException {
        return new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    }

    @Test
    void marshallAndUnmarshallMagicNumber() throws IOException {
        // given, when
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bytes);
        NetworkMessage.marshallMagicNumber(out);
        out.flush();

        ObjectInputStream in = asInput(bytes);

        // then
        assertDoesNotThrow(() -> NetworkMessage.unmarshallMagicNumber(in));
    }

    @Test
    void unmarshallAnErroneousMagicNumber() throws IOException {
        // given
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bytes);
        out.writeByte(169);
        out.flush();

        ObjectInputStream in = asInput(bytes);

        // when, then
        assertThrows(IOException.class, () -> NetworkMessage.unmarshallMagicNumber(in));
    }

    @Test
    void marshallEnumWritesTheOrdinal() throws IOException {
        // given
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bytes);
        NetworkMessage.marshallEnum(out, CompressionMethodForTest.PACKBITS);
        NetworkMessage.marshallEnum(out, CompressionMethodForTest.XZ);
        out.flush();

        // when
        ObjectInputStream in = asInput(bytes);

        // then
        assertEquals(CompressionMethodForTest.PACKBITS.ordinal(), in.readByte());
        assertEquals(CompressionMethodForTest.XZ.ordinal(), in.readByte());
    }

    @Test
    void unmarshallEnumByOrdinal() throws IOException {
        // given
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bytes);
        NetworkMessage.marshallEnum(out, CompressionMethodForTest.XZ);
        out.flush();

        // when
        CompressionMethodForTest value = NetworkMessage.unmarshallEnum(asInput(bytes), CompressionMethodForTest.class);

        // then
        assertEquals(CompressionMethodForTest.XZ, value);
    }

    @Test
    void unmarshallAnUnknownEnumOrdinal() throws IOException {
        // given
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bytes);
        out.writeByte(CompressionMethodForTest.values().length); // out of range
        out.flush();

        ObjectInputStream in = asInput(bytes);

        // when, then
        assertThrows(IOException.class, () -> NetworkMessage.unmarshallEnum(in, CompressionMethodForTest.class));
    }

    @Test
    void unmarshallANegativeEnumOrdinal() throws IOException {
        // given
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bytes);
        out.writeByte(-1); // out of range
        out.flush();

        ObjectInputStream in = asInput(bytes);

        // when, then
        assertThrows(IOException.class, () -> NetworkMessage.unmarshallEnum(in, CompressionMethodForTest.class));
    }

    private enum CompressionMethodForTest {
        PACKBITS, XZ
    }
}
