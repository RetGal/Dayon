package mpo.dayon.common.network.message;

import mpo.dayon.common.squeeze.CompressionMethod;
import org.junit.jupiter.api.Test;

import java.io.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NetworkCompressorConfigurationMessageTest {

    private static NetworkCompressorConfigurationMessage unmarshall(int maxSize, int purgeSize) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(byteArrayOutputStream);
        NetworkMessage.marshallEnum(out, CompressionMethod.ZIP);
        out.writeByte(1);
        out.writeInt(maxSize);
        out.writeInt(purgeSize);
        out.flush();
        return NetworkCompressorConfigurationMessage.unmarshall(
                new ObjectInputStream(new ByteArrayInputStream(byteArrayOutputStream.toByteArray())));
    }

    @Test
    void testUnmarshall() throws IOException {
        // given
        CompressionMethod method = CompressionMethod.ZIP;
        boolean useCache = true;
        int maxSize = 1024;
        int purgeSize = 512;

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(byteArrayOutputStream);
        NetworkMessage.marshallEnum(out, method);
        out.writeByte(useCache ? 1 : 0);
        out.writeInt(maxSize);
        out.writeInt(purgeSize);
        out.flush();

        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(byteArrayOutputStream.toByteArray());
        ObjectInputStream in = new ObjectInputStream(byteArrayInputStream);

        // when
        NetworkCompressorConfigurationMessage message = NetworkCompressorConfigurationMessage.unmarshall(in);

        // then
        assertEquals(method, message.getConfiguration().getMethod());
        assertEquals(useCache, message.getConfiguration().useCache());
        assertEquals(maxSize, message.getConfiguration().getCacheMaxSize());
        assertEquals(purgeSize, message.getConfiguration().getCachePurgeSize());
    }

    @Test
    void acceptsTheCacheSizeBoundaries() throws IOException {
        assertEquals(2, unmarshall(2, 1).getConfiguration().getCacheMaxSize());
        assertEquals(100000, unmarshall(100000, 99999).getConfiguration().getCacheMaxSize());
    }

    @Test
    void rejectsACacheMaxSizeOutsideTheRange() {
        assertThrows(IOException.class, () -> unmarshall(0, 0));
        assertThrows(IOException.class, () -> unmarshall(-1, 0));
        assertThrows(IOException.class, () -> unmarshall(129601, 1));
    }

    @Test
    void rejectsACachePurgeSizeOutsideTheRange() {
        assertThrows(IOException.class, () -> unmarshall(100, 0));
        assertThrows(IOException.class, () -> unmarshall(100, -1));
    }

    @Test
    void rejectsACachePurgeSizeThatIsNotSmallerThanTheMaxSize() {
        assertThrows(IOException.class, () -> unmarshall(100, 100));
        assertThrows(IOException.class, () -> unmarshall(100, 101));
    }
}
