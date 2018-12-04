package mpo.dayon.common.network.message;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * From the assisted to the assistant.
 */
public class NetworkMouseLocationMessage extends NetworkMessage {
	private final int x;

	private final int y;

	public NetworkMouseLocationMessage(int x, int y) {
		this.x = x;
		this.y = y;
	}

	public NetworkMessageType getType() {
		return NetworkMessageType.MOUSE_LOCATION;
	}

	public int getX() {
		return x;
	}

	public int getY() {
		return y;
	}

	/**
	 * Take into account some extra-info sent over the network with the actual
	 * payload ...
	 */
	public int getWireSize() {
		return 5; // type (byte) + x (short) + y (short)
	}

	public void marshall(DataOutputStream out) throws IOException {
		marshallEnum(out, NetworkMessageType.class, getType());

		out.writeShort(x);
		out.writeShort(y);
	}

	public static NetworkMouseLocationMessage unmarshall(DataInputStream in) throws IOException {
		final int x = in.readShort();
		final int y = in.readShort();

		return new NetworkMouseLocationMessage(x, y);
	}

	public String toString() {
		return String.format("[x:%d] [y:%s]", x, y);
	}
}