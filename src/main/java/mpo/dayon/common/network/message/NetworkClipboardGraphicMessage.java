package mpo.dayon.common.network.message;

import mpo.dayon.common.network.TransferableImage;

import java.awt.datatransfer.DataFlavor;
import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class NetworkClipboardGraphicMessage extends NetworkMessage {

    private static final ObjectInputFilter TRANSFERABLE_IMAGE_FILTER = info -> {
        Class<?> serialClass = info.serialClass();
        if (serialClass == null) {
            return ObjectInputFilter.Status.UNDECIDED;
        }
        return serialClass == TransferableImage.class
                ? ObjectInputFilter.Status.ALLOWED
                : ObjectInputFilter.Status.REJECTED;
    };

    private final TransferableImage payload;
    private final int size;

    public NetworkClipboardGraphicMessage(TransferableImage payload) {
        this.payload = new TransferableImage(payload.getTransferData(DataFlavor.imageFlavor));
        // this is just a rather rough estimation
        this.size = payload.getTransferData(DataFlavor.imageFlavor).getData().getDataBuffer().getSize() * 4;
    }

    public static void installImageObjectInputFilter(ObjectInputStream in) {
        in.setObjectInputFilter(TRANSFERABLE_IMAGE_FILTER);
    }

    @SuppressWarnings("squid:S5135") // false positive: see installImageObjectInputFilter, called by the NetworkEngine
    public static NetworkClipboardGraphicMessage unmarshall(ObjectInputStream in) throws IOException, ClassNotFoundException {
        return new NetworkClipboardGraphicMessage((TransferableImage) in.readUnshared());
    }

    public TransferableImage getGraphic() {
        return new TransferableImage(payload.getTransferData(DataFlavor.imageFlavor));
    }

    @Override
    public NetworkMessageType getType() {
        return NetworkMessageType.CLIPBOARD_GRAPHIC;
    }

    @Override
    public int getWireSize() {
        return 1 + size;
    }

    @Override
    public void marshall(ObjectOutputStream out) throws IOException {
        marshallEnum(out, getType());
        out.writeObject(payload);
    }

    @Override
    public String toString() {
        return "Clipboard graphic";
    }

}
