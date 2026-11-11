package mpo.dayon.common.network;

import mpo.dayon.common.log.Log;

import javax.imageio.ImageIO;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class TransferableImage implements Transferable, Serializable {

    private static final int MAX_IMAGE_SIZE_MB = 50;
    private static final long MAX_IMAGE_SIZE = MAX_IMAGE_SIZE_MB * 1024L * 1024;
    private static final String IMAGE_TOO_LARGE = "Image data exceeds " + MAX_IMAGE_SIZE_MB + " MB";

    private transient BufferedImage image;

    public TransferableImage(BufferedImage image) {
        this.image = image.getSubimage(0, 0, image.getWidth(), image.getHeight());
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        ImageIO.write(image, "png", out);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        final LimitedInputStream limited = new LimitedInputStream(in);
        try {
            image = ImageIO.read(limited);
        } catch (IOException e) {
            if (limited.exceeded()) {
                throw new IOException(IMAGE_TOO_LARGE, e);
            }
            throw e;
        }
    }

    public DataFlavor[] getTransferDataFlavors() {
        return new DataFlavor[] { DataFlavor.imageFlavor };
    }

    public boolean isDataFlavorSupported(DataFlavor flavor) {
        return DataFlavor.imageFlavor.equals(flavor);
    }

    public BufferedImage getTransferData(DataFlavor flavor) {
        if (!DataFlavor.imageFlavor.equals(flavor)) {
            Log.error(new UnsupportedFlavorException(flavor).getMessage());
        }
        return image.getSubimage(0, 0, image.getWidth(), image.getHeight());
    }

    private static class LimitedInputStream extends FilterInputStream {

        private long count;

        LimitedInputStream(InputStream in) {
            super(in);
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b != -1 && ++count > MAX_IMAGE_SIZE) {
                throw new IOException(IMAGE_TOO_LARGE);
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int read = super.read(b, off, len);
            if (read > 0 && (count += read) > MAX_IMAGE_SIZE) {
                throw new IOException(IMAGE_TOO_LARGE);
            }
            return read;
        }

        boolean exceeded() {
            return count > MAX_IMAGE_SIZE;
        }
    }
}
