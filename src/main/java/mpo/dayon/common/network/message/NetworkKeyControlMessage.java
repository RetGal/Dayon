package mpo.dayon.common.network.message;

import java.io.*;

/**
 * From the assistant to the assisted.
 */
public class NetworkKeyControlMessage extends NetworkMessage {
	public enum KeyState {
		PRESSED, RELEASED,
	}

	private static final int PRESSED = 1;

	private static final int RELEASED = 1 << 1;

	private final int info;

	private final int keyCode;
	
	private final char keyChar;

	public NetworkKeyControlMessage(KeyState buttonState, int keyCode, char keyChar) {
		this(buttonState == KeyState.PRESSED ? PRESSED : RELEASED, keyCode, keyChar);
	}

	private NetworkKeyControlMessage(int info, int keyCode, char keyChar) {
		this.info = info;
		this.keyCode = keyCode;
		this.keyChar = keyChar;
	}

	@Override
    public NetworkMessageType getType() {
		return NetworkMessageType.KEY_CONTROL;
	}

	public int getKeyCode() {
		return keyCode;
	}
	
	public char getKeyChar() {
		return keyChar;
	}

	public boolean isPressed() {
		return (info & PRESSED) == PRESSED;
	}

	public boolean isReleased() {
		return (info & RELEASED) == RELEASED;
	}

	@Override
    public int getWireSize() {
		return 11; // type (byte) + info (int) + keyCode (int) + keyChar (char)
	}

	@Override
    public void marshall(ObjectOutputStream out) throws IOException {
		marshallEnum(out, getType());
		out.writeInt(info);
		out.writeInt(keyCode);
		out.writeChar(keyChar);
	}

	public static NetworkKeyControlMessage unmarshall(ObjectInputStream in) throws IOException {
		final int info = in.readInt();
		final int keyCode = in.readInt();
		return new NetworkKeyControlMessage(info, keyCode, in.readChar());
	}

	public String toString() {
		return String.format("%s [%d] [%s]", toStringPressed(), keyCode, keyChar);
	}

	private String toStringPressed() {
		if (isPressed()) {
			return "PRESSED";
		}

		if (isReleased()) {
			return "RELEASED";
		}
		return "";
	}

}