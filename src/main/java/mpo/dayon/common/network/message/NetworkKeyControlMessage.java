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

	private final int keycode;
	
	private final char keychar;

	public NetworkKeyControlMessage(KeyState buttonState, int keycode, char keychar) {
		this(buttonState == KeyState.PRESSED ? PRESSED : RELEASED, keycode, keychar);
	}

	private NetworkKeyControlMessage(int info, int keycode, char keychar) {
		this.info = info;
		this.keycode = keycode;
		this.keychar = keychar;
	}

	@Override
    public NetworkMessageType getType() {
		return NetworkMessageType.KEY_CONTROL;
	}

	public int getKeyCode() {
		return keycode;
	}
	
	public char getKeyChar() {
		return keychar;
	}

	public boolean isPressed() {
		return (info & PRESSED) == PRESSED;
	}

	public boolean isReleased() {
		return (info & RELEASED) == RELEASED;
	}

	@Override
    public int getWireSize() {
		return 11; // type (byte) + info (int) + keycode (int) + keychar (char)
	}

	@Override
    public void marshall(ObjectOutputStream out) throws IOException {
		marshallEnum(out, getType());
		out.writeInt(info);
		out.writeInt(keycode);
		out.writeChar(keychar);
	}

	public static NetworkKeyControlMessage unmarshall(ObjectInputStream in) throws IOException {
		final int info = in.readInt();
		final int keycode = in.readInt();
		final char keychar = in.readChar();
		return new NetworkKeyControlMessage(info, keycode, keychar);
	}

	public String toString() {
		return String.format("%s [%d] [%s]", toStringPressed(), keycode, keychar);
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