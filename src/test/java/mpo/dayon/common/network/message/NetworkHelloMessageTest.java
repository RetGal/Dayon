package mpo.dayon.common.network.message;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.*;

import static org.junit.jupiter.api.Assertions.*;

class NetworkHelloMessageTest {

    @Test
    void testHelloMessage() {
        // given
        final int major = 0;
        final int minor = 0;
        final char osId = 'l';

        // when
        final NetworkHelloMessage message = new NetworkHelloMessage(major, minor, osId);

        // then
        assertEquals(major, message.getMajor());
        assertEquals(minor, message.getMinor());
        assertEquals(osId, message.getOsId());
    }

    @ParameterizedTest
    @CsvSource({ "13, 2", "12, 0", "11, 3" })
    void unmarshallHelloMessageFromLegacyVersion(int major, int minor) throws IOException {
        // given
        String fileName = "tmp";
        ObjectOutputStream output = new ObjectOutputStream(new FileOutputStream(fileName));
        output.writeInt(major);
        output.writeInt(minor);
        output.close();
        ObjectInputStream objStream = new ObjectInputStream(new FileInputStream(fileName));

        // when
        NetworkHelloMessage message = NetworkHelloMessage.unmarshall(objStream);
        objStream.close();

        // then
        assertEquals(major, message.getMajor());
        assertEquals(minor, message.getMinor());
        assertEquals('x', message.getOsId(), "Should use default value without trying to read osId from the stream");
    }



    @ParameterizedTest
    @CsvSource({ "13, 1, l", "0, 0, w" })
    void unmarshallHelloMessageFromSupportedVersion(int major, int minor, char osId) throws IOException {
        // given
        String fileName = "tmp";
        ObjectOutputStream output = new ObjectOutputStream(new FileOutputStream(fileName));
        output.writeInt(major);
        output.writeInt(minor);
        output.writeChar(osId);
        output.close();
        ObjectInputStream objStream = new ObjectInputStream(new FileInputStream(fileName));

        // when
        NetworkHelloMessage message = NetworkHelloMessage.unmarshall(objStream);
        objStream.close();

        // then
        assertEquals(major, message.getMajor());
        assertEquals(minor, message.getMinor());
        assertEquals(osId, message.getOsId());
    }
}