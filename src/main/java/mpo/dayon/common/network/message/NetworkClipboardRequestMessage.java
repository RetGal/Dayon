package mpo.dayon.common.network.message;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;

import static mpo.dayon.common.network.message.NetworkMessageType.CLIPBOARD_REQUEST;

public class NetworkClipboardRequestMessage extends NetworkMessage {
    @Override
    public NetworkMessageType getType() {
        return CLIPBOARD_REQUEST;
    }

    @Override
    public int getWireSize() {
        return 1; // type (byte)
    }

    @Override
    public void marshall(ObjectOutputStream out) throws IOException {
        marshallEnum(out, NetworkMessageType.class, getType());
    }

    @Override
    public String toString() {
        return "Clipboard request";
    }
}
