package mpo.dayon.common.network.message;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class NetworkClipboardTextMessageTest {

    @Test
    void marshallAndUnmarshall() throws IOException {
        // given
        final NetworkClipboardTextMessage message = new NetworkClipboardTextMessage("unicorn \uD83E\uDD84", 10);
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final ObjectOutputStream out = new ObjectOutputStream(bytes);
        message.marshall(out);
        out.flush();

        // when
        final ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        assertEquals(NetworkMessageType.CLIPBOARD_TEXT, NetworkMessage.unmarshallEnum(in, NetworkMessageType.class));
        final NetworkClipboardTextMessage unmarshalled = NetworkClipboardTextMessage.unmarshall(in);

        // then
        assertEquals("unicorn \uD83E\uDD84", unmarshalled.getText());
    }

    @Test
    void getWireSizeContainsTheTypeAndTheTextLength() {
        // given
        final NetworkClipboardTextMessage message = new NetworkClipboardTextMessage("abc", 3);

        // when, then
        assertEquals(4, message.getWireSize());
        assertEquals(NetworkMessageType.CLIPBOARD_TEXT, message.getType());
        assertEquals("Clipboard text", message.toString());
    }
}
