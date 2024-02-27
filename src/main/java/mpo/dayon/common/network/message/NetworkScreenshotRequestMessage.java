package mpo.dayon.common.network.message;

import java.io.IOException;
import java.io.ObjectOutputStream;

public class NetworkScreenshotRequestMessage extends NetworkMessage {
    @Override
    public NetworkMessageType getType() {
        return NetworkMessageType.SCREENSHOT_REQUEST;
    }

    @Override
    public int getWireSize() {
        return 1; // type (byte)
    }

    @Override
    public void marshall(ObjectOutputStream out) throws IOException {
        marshallEnum(out, getType());
    }

    @Override
    public String toString() {
        return "Screenshot request message";
    }
}
