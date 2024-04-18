package mpo.dayon.common.network;

import mpo.dayon.common.log.Log;

import javax.imageio.ImageIO;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class TransferableImage implements Transferable, Serializable {

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
        image = ImageIO.read(in);
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
}
