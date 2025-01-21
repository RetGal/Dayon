package mpo.dayon.common.network.message;

import mpo.dayon.common.squeeze.CompressionMethod;
import org.junit.jupiter.api.Test;

import java.io.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NetworkCompressorConfigurationMessageTest {

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
}
