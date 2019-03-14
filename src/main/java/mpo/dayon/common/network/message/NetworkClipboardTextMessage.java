package mpo.dayon.common.network.message;

import java.io.*;

public class NetworkClipboardTextMessage extends NetworkMessage {

    private final String payload;
    private final int size;

    public NetworkClipboardTextMessage(String payload, int size) {
        this.payload = payload;
        this.size = size;
    }

    public static NetworkClipboardTextMessage unmarshall(ObjectInputStream in) throws IOException {
        String text = in.readUTF();
        return new NetworkClipboardTextMessage(text, text.length());
    }

    public String getText() {
        return payload;
    }

    @Override
    public NetworkMessageType getType() {
        return NetworkMessageType.CLIPBOARD_TEXT;
    }

    @Override
    public int getWireSize() {
        return 1 + size;  // type (byte) + payload
    }

    @Override
    public void marshall(ObjectOutputStream out) throws IOException {
        marshallEnum(out, getType());
        out.writeUTF(payload);
    }

    @Override
    public String toString() {
        return "Clipboard text";
    }
}
