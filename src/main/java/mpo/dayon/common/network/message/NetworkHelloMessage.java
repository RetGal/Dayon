package mpo.dayon.common.network.message;

import java.io.*;

public class NetworkHelloMessage extends NetworkMessage {
	private final int major;

	private final int minor;

	public NetworkHelloMessage(int major, int minor) {
		this.major = major;
		this.minor = minor;
	}

	@Override
    public NetworkMessageType getType() {
		return NetworkMessageType.HELLO;
	}

	public int getMajor() {
		return major;
	}

	public int getMinor() {
		return minor;
	}

	/**
	 * Take into account some extra-info sent over the network with the actual
	 * payload ...
	 */
	@Override
    public int getWireSize() {
		return 9; // type (byte) + major (int) + minor (int)
	}

	@Override
    public void marshall(ObjectOutputStream out) throws IOException {
		marshallEnum(out, NetworkMessageType.class, getType());

		out.writeInt(major);
		out.writeInt(minor);
	}

	public static NetworkHelloMessage unmarshall(ObjectInputStream in) throws IOException {
		final int major = in.readInt();
		final int minor = in.readInt();

		return new NetworkHelloMessage(major, minor);
	}

	public String toString() {
		return String.format("[major:%d] [minor:%s]", major, minor);
	}
}