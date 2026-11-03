package mpo.dayon.common.network.message;

import mpo.dayon.common.buffer.MemByteBuffer;
import mpo.dayon.common.compressor.CompressorEngineConfiguration;
import mpo.dayon.common.squeeze.CompressionMethod;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class NetworkCaptureMessageTest {

    private static ObjectInputStream asInput(ByteArrayOutputStream bytes) throws IOException {
        return new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    }

    private static NetworkCaptureMessage roundTrip(NetworkCaptureMessage message) throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final ObjectOutputStream out = new ObjectOutputStream(bytes);
        message.marshall(out);
        out.flush();

        final ObjectInputStream in = asInput(bytes);
        assertEquals(NetworkMessageType.CAPTURE, NetworkMessage.unmarshallEnum(in, NetworkMessageType.class));
        return NetworkCaptureMessage.unmarshall(in);
    }

    private static MemByteBuffer payload() {
        final MemByteBuffer payload = new MemByteBuffer();
        payload.writes(1, 2, 3);
        return payload;
    }

    @Test
    void marshallAndUnmarshallWithoutCompressionConfiguration() throws IOException {
        // given
        final NetworkCaptureMessage message = new NetworkCaptureMessage(42, CompressionMethod.ZIP, null, payload());

        // when
        final NetworkCaptureMessage unmarshalled = roundTrip(message);

        // then
        assertEquals(42, unmarshalled.getId());
        assertEquals(CompressionMethod.ZIP, unmarshalled.getCompressionMethod());
        assertNull(unmarshalled.getCompressionConfiguration());
        assertArrayEquals(new byte[] { 1, 2, 3 }, unmarshalled.getPayload().getInternal());
    }

    @Test
    void marshallAndUnmarshallWithCompressionConfiguration() throws IOException {
        // given
        final CompressorEngineConfiguration configuration = new CompressorEngineConfiguration(CompressionMethod.XZ, true, 1024, 512);
        final NetworkCaptureMessage message = new NetworkCaptureMessage(7, CompressionMethod.XZ, configuration, payload());

        // when
        final NetworkCaptureMessage unmarshalled = roundTrip(message);

        // then
        assertEquals(7, unmarshalled.getId());
        assertEquals(CompressionMethod.XZ, unmarshalled.getCompressionMethod());
        assertEquals(configuration, unmarshalled.getCompressionConfiguration());
        assertArrayEquals(new byte[] { 1, 2, 3 }, unmarshalled.getPayload().getInternal());
    }

    @Test
    void getWireSizeDependsOnTheCompressionConfiguration() {
        // given
        final CompressorEngineConfiguration configuration = new CompressorEngineConfiguration(CompressionMethod.ZIP, false, 1024, 512);

        // when, then
        assertEquals(11 + 3, new NetworkCaptureMessage(1, CompressionMethod.ZIP, null, payload()).getWireSize());
        assertEquals(10 + 11 + 3, new NetworkCaptureMessage(1, CompressionMethod.ZIP, configuration, payload()).getWireSize());
    }

    @Test
    void toStringContainsTheIdAndThePayloadSize() {
        // given
        final NetworkCaptureMessage message = new NetworkCaptureMessage(42, CompressionMethod.NONE, null, payload());

        // when, then
        assertEquals("[id:42][24.00 bit]", message.toString());
        assertEquals(NetworkMessageType.CAPTURE, message.getType());
        assertEquals(CompressionMethod.NONE, message.getCompressionMethod());
        assertNull(message.getCompressionConfiguration());
    }
}
