package mpo.dayon.common.network.message;

import mpo.dayon.common.network.TransferableImage;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.datatransfer.DataFlavor;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class NetworkClipboardGraphicMessageTest {

    private static BufferedImage image() {
        final BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, Color.RED.getRGB());
        image.setRGB(1, 0, Color.GREEN.getRGB());
        image.setRGB(0, 1, Color.BLUE.getRGB());
        image.setRGB(1, 1, Color.WHITE.getRGB());
        return image;
    }

    @Test
    void marshallAndUnmarshall() throws IOException, ClassNotFoundException {
        // given
        final NetworkClipboardGraphicMessage message = new NetworkClipboardGraphicMessage(new TransferableImage(image()));
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final ObjectOutputStream out = new ObjectOutputStream(bytes);
        message.marshall(out);
        out.flush();

        // when
        final ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        assertEquals(NetworkMessageType.CLIPBOARD_GRAPHIC, NetworkMessage.unmarshallEnum(in, NetworkMessageType.class));
        final NetworkClipboardGraphicMessage unmarshalled = NetworkClipboardGraphicMessage.unmarshall(in);

        // then
        final BufferedImage restored = unmarshalled.getGraphic().getTransferData(DataFlavor.imageFlavor);
        assertEquals(2, restored.getWidth());
        assertEquals(2, restored.getHeight());
        assertEquals(Color.RED.getRGB(), restored.getRGB(0, 0));
        assertEquals(Color.GREEN.getRGB(), restored.getRGB(1, 0));
        assertEquals(Color.BLUE.getRGB(), restored.getRGB(0, 1));
        assertEquals(Color.WHITE.getRGB(), restored.getRGB(1, 1));
    }

    @Test
    void getWireSizeContainsTheTypeAndTheEstimatedImageSize() {
        // given
        final NetworkClipboardGraphicMessage message = new NetworkClipboardGraphicMessage(new TransferableImage(image()));

        // when, then
        assertEquals(1 + 4 * 4, message.getWireSize());
        assertEquals(NetworkMessageType.CLIPBOARD_GRAPHIC, message.getType());
        assertEquals("Clipboard graphic", message.toString());
    }

    @Test
    void getGraphicReturnsACopyOfTheImage() {
        // given
        final NetworkClipboardGraphicMessage message = new NetworkClipboardGraphicMessage(new TransferableImage(image()));

        // when
        final BufferedImage first = message.getGraphic().getTransferData(DataFlavor.imageFlavor);
        final BufferedImage second = message.getGraphic().getTransferData(DataFlavor.imageFlavor);

        // then
        assertNotSame(first, second);
        assertEquals(first.getRGB(0, 0), second.getRGB(0, 0));
    }
}
