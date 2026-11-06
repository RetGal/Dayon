package mpo.dayon.common.network.message;

import mpo.dayon.common.capture.CaptureEngineConfiguration;
import mpo.dayon.common.capture.Gray8Bits;
import org.junit.jupiter.api.Test;

import java.io.*;

import static org.junit.jupiter.api.Assertions.*;

class NetworkCaptureConfigurationMessageTest {

    private static NetworkCaptureConfigurationMessage roundTrip(NetworkCaptureConfigurationMessage message) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bytes);
        NetworkMessage.marshallMagicNumber(out);
        message.marshall(out);
        out.flush();
        ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        NetworkMessage.unmarshallMagicNumber(in);
        NetworkMessage.unmarshallEnum(in, NetworkMessageType.class);
        return NetworkCaptureConfigurationMessage.unmarshall(in);
    }

    private static NetworkCaptureConfigurationMessage unmarshall(int tick) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bytes);
        NetworkMessage.marshallEnum(out, Gray8Bits.X_128);
        out.writeInt(tick);
        out.writeShort(0);
        out.flush();
        return NetworkCaptureConfigurationMessage.unmarshall(
                new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())));
    }

    @Test
    void acceptsTheTickBoundaries() throws IOException {
        assertEquals(1, unmarshall(1).getConfiguration().getCaptureTick());
        assertEquals(2000, unmarshall(2000).getConfiguration().getCaptureTick());
    }

    @Test
    void rejectsATickOutsideTheRange() {
        assertThrows(IOException.class, () -> unmarshall(0));
        assertThrows(IOException.class, () -> unmarshall(-1));
        assertThrows(IOException.class, () -> unmarshall(100001));
    }

    @Test
    void getters() {
        // given
        CaptureEngineConfiguration configuration = new CaptureEngineConfiguration(150, Gray8Bits.X_64, true);

        // when
        NetworkCaptureConfigurationMessage message = new NetworkCaptureConfigurationMessage(configuration, false);

        // then
        assertEquals(NetworkMessageType.CAPTURE_CONFIGURATION, message.getType());
        assertEquals(configuration, message.getConfiguration());
        assertEquals(8, message.getWireSize());
        assertEquals("[quantization:X_64][tick:150][colors:true]", message.toString());
    }

    @Test
    void monochromePeerHasAWireSizeBackwardsCompatible() {
        // given
        CaptureEngineConfiguration configuration = new CaptureEngineConfiguration(150, Gray8Bits.X_64, true);

        // when
        NetworkCaptureConfigurationMessage message = new NetworkCaptureConfigurationMessage(configuration, true);

        // then
        assertEquals(6, message.getWireSize(), "no colors short is sent to a monochrome peer");
    }

    @Test
    void marshallUnmarshallColored() throws IOException {
        // given
        NetworkCaptureConfigurationMessage message = new NetworkCaptureConfigurationMessage(new CaptureEngineConfiguration(120, Gray8Bits.X_32, true), false);

        // when
        NetworkCaptureConfigurationMessage unmarshalled = roundTrip(message);

        // then
        assertEquals(120, unmarshalled.getConfiguration().getCaptureTick());
        assertEquals(Gray8Bits.X_32, unmarshalled.getConfiguration().getCaptureQuantization());
        assertTrue(unmarshalled.getConfiguration().isCaptureColors());
    }

    @Test
    void marshallUnmarshallMonochrome() throws IOException {
        // given
        NetworkCaptureConfigurationMessage message = new NetworkCaptureConfigurationMessage(new CaptureEngineConfiguration(200, Gray8Bits.X_256, false), false);

        // when
        NetworkCaptureConfigurationMessage unmarshalled = roundTrip(message);

        // then
        assertEquals(200, unmarshalled.getConfiguration().getCaptureTick());
        assertEquals(Gray8Bits.X_256, unmarshalled.getConfiguration().getCaptureQuantization());
        assertFalse(unmarshalled.getConfiguration().isCaptureColors());
    }
}
