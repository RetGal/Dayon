package mpo.dayon.common.network.message;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class NetworkResizeScreenMessage extends NetworkMessage {
	private final int width;
	private final int heigth;

	public NetworkResizeScreenMessage(int width, int heigth) {
		this.width = width;
		this.heigth = heigth;
	}

	@Override
    public NetworkMessageType getType() {
		return NetworkMessageType.RESIZE;
	}

	public int getWidth() {
		return width;
	}

	public int getHeigth() {
		return heigth;
	}

	/**
	 * Take into account some extra-info sent over the network with the actual
	 * payload ...
	 */
	@Override
    public int getWireSize() {
		return 9; // type (byte) + newWidth (int) + newHeight (int)
	}

	@Override
    public void marshall(ObjectOutputStream out) throws IOException {
		marshallEnum(out, getType());
		out.writeInt(width);
		out.writeInt(heigth);
	}

	public static NetworkResizeScreenMessage unmarshall(ObjectInputStream in) throws IOException {
		final int width = in.readInt();
		final int height = in.readInt();
		return new NetworkResizeScreenMessage(width, height);
	}

	public String toString() {
		return String.format("[width:%d height:%d]", width, heigth);
	}
}