package mpo.dayon.common.network.message;

import java.io.IOException;
import java.io.ObjectOutputStream;

public class NetworkGoodbyeMessage extends NetworkMessage {

	@Override
    public NetworkMessageType getType() {
		return NetworkMessageType.GOODBYE;
	}

	@Override
    public int getWireSize() {
		return 1; // type (byte)
	}

	@Override
    public void marshall(ObjectOutputStream out) throws IOException {
		marshallEnum(out, getType());
	}

	public static NetworkGoodbyeMessage unmarshall() throws IOException {
		return new NetworkGoodbyeMessage();
	}

	public String toString() {
		return "Goodbye message";
	}
}