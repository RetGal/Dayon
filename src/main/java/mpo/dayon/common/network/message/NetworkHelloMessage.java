package mpo.dayon.common.network.message;

import java.io.*;

public class NetworkHelloMessage extends NetworkMessage {
	private final int major;

	private final int minor;

	private final char osId;

	private final String inputLocale;

	public NetworkHelloMessage(int major, int minor, char osId, String inputLocale) {
		this.major = major;
		this.minor = minor;
		this.osId = osId;
        this.inputLocale = inputLocale;
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

	public String getInputLocale() {
		return inputLocale;
	}

	/**
	 * Take into account some extra-info sent over the network with the actual
	 * payload ...
	 */
	@Override
    public int getWireSize() {
		return 11 + inputLocale.length(); // (type (byte) + major (int) + minor (int) + osId (char)) + localeId (String
	}

	@Override
    public void marshall(ObjectOutputStream out) throws IOException {
		marshallEnum(out, getType());
		out.writeInt(major);
		out.writeInt(minor);
		out.writeChar(osId);
		out.writeUTF(inputLocale);
	}

	public static NetworkHelloMessage unmarshall(ObjectInputStream in) throws IOException {
		final int major = in.readInt();
		final int minor = in.readInt();
		if (major > 13 || (major == 13 && minor > 0) || major == 0 ) {
			char osId =  in.readChar();
			String localeId = in.readUTF();
			return new NetworkHelloMessage(major, minor, osId, localeId);
		}
		return new NetworkHelloMessage(major, minor, 'x', "");
	}

	public String toString() {
		return String.format("[major:%d][minor:%d][osId:%c][inputLocale:%s]", major, minor, osId, inputLocale);
	}
}