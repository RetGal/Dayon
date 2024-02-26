package mpo.dayon.common.network.message;

import java.io.*;

public class NetworkHelloMessage extends NetworkMessage {
	private final int major;

	private final int minor;

	private final char osId;

	public NetworkHelloMessage(int major, int minor, char osId) {
		this.major = major;
		this.minor = minor;
		this.osId = osId;
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

	public char getOsId() {
		return osId;
	}

	/**
	 * Take into account some extra-info sent over the network with the actual
	 * payload ...
	 */
	@Override
    public int getWireSize() {
		return 11; // type (byte) + major (int) + minor (int) + osId (char)
	}

	@Override
    public void marshall(ObjectOutputStream out) throws IOException {
		marshallEnum(out, getType());
		out.writeInt(major);
		out.writeInt(minor);
		out.writeChar(osId);
	}

	public static NetworkHelloMessage unmarshall(ObjectInputStream in) throws IOException {
		final int major = in.readInt();
		final int minor = in.readInt();
		char osId = major > 13 || (major == 13 && minor > 0) || major == 0 ? in.readChar() : 'x';
		return new NetworkHelloMessage(major, minor, osId);
	}

	public String toString() {
		return String.format("[major:%d] [minor:%s] [osId:%c]", major, minor, osId);
	}
}