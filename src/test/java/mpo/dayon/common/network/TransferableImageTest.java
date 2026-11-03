package mpo.dayon.common.network;

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

class TransferableImageTest {

    private static BufferedImage image() {
        final BufferedImage image = new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB);
        image.setRGB(1, 1, Color.MAGENTA.getRGB());
        return image;
    }

    @Test
    void supportsTheImageFlavorOnly() {
        // given
        final TransferableImage transferable = new TransferableImage(image());

        // when, then
        assertArrayEquals(new DataFlavor[] { DataFlavor.imageFlavor }, transferable.getTransferDataFlavors());
        assertTrue(transferable.isDataFlavorSupported(DataFlavor.imageFlavor));
        assertFalse(transferable.isDataFlavorSupported(DataFlavor.stringFlavor));
    }

    @Test
    void returnsTheImage() {
        // given
        final TransferableImage transferable = new TransferableImage(image());

        // when
        final BufferedImage image = transferable.getTransferData(DataFlavor.imageFlavor);

        // then
        assertEquals(3, image.getWidth());
        assertEquals(2, image.getHeight());
        assertEquals(Color.MAGENTA.getRGB(), image.getRGB(1, 1));
    }

    @Test
    void returnsTheImageForAnUnsupportedFlavor() {
        // given
        final TransferableImage transferable = new TransferableImage(image());

        // when, then
        assertNotNull(transferable.getTransferData(DataFlavor.stringFlavor));
    }

    @Test
    void isSerializable() throws IOException, ClassNotFoundException {
        // given
        final TransferableImage transferable = new TransferableImage(image());
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        // when
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(transferable);
        }
        final TransferableImage restored;
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = (TransferableImage) in.readObject();
        }

        // then
        final BufferedImage image = restored.getTransferData(DataFlavor.imageFlavor);
        assertEquals(3, image.getWidth());
        assertEquals(2, image.getHeight());
        assertEquals(Color.MAGENTA.getRGB(), image.getRGB(1, 1));
    }
}
