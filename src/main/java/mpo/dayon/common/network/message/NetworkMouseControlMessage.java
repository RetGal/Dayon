package mpo.dayon.common.network.message;

import java.io.*;

/**
 * From the assistant to the assisted.
 */
public class NetworkMouseControlMessage extends NetworkMessage {
	public enum ButtonState {
		PRESSED, RELEASED,
	}

	private static final int PRESSED = 1;

	private static final int RELEASED = 1 << 1;

	public static final int BUTTON1 = 1 << 2;

	public static final int BUTTON2 = 1 << 3;

	public static final int BUTTON3 = 1 << 4;
	
	public static final int UNDEFINED = -1;

	private static final int WHEEL = 1 << 5;

	private final int x;

	private final int y;

	private final int info;

	private final int rotations;

	public NetworkMouseControlMessage(int x, int y) {
		this(x, y, 0, 0);
	}

	public NetworkMouseControlMessage(int x, int y, ButtonState buttonState, int button) {
		this(x, y, (buttonState == ButtonState.PRESSED ? PRESSED : RELEASED) | button, 0);
	}

	public NetworkMouseControlMessage(int x, int y, int rotations) {
		this(x, y, WHEEL, rotations);
	}

	private NetworkMouseControlMessage(int x, int y, int info, int rotations) {
		this.x = x;
		this.y = y;
		this.info = info;
		this.rotations = rotations;
	}

	@Override
    public NetworkMessageType getType() {
		return NetworkMessageType.MOUSE_CONTROL;
	}

	public int getX() {
		return x;
	}

	public int getY() {
		return y;
	}

	public int getRotations() {
		return rotations;
	}

	public boolean isPressed() {
		return (info & PRESSED) == PRESSED;
	}

	public boolean isReleased() {
		return (info & RELEASED) == RELEASED;
	}

	public boolean isButton1() {
		return (info & BUTTON1) == BUTTON1;
	}

	public boolean isButton2() {
		return (info & BUTTON2) == BUTTON2;
	}

	public boolean isButton3() {
		return (info & BUTTON3) == BUTTON3;
	}

	public boolean isWheel() {
		return (info & WHEEL) == WHEEL;
	}

	@Override
    public int getWireSize() {
		if (isWheel()) {
			return 13; // type (byte) + x (short) + y (short) + info (int) +
						// rotations (int)
		}
		return 9; // type (byte) + x (short) + y (short) + info (int)
	}

	@Override
    public void marshall(ObjectOutputStream out) throws IOException {
		marshallEnum(out, getType());
		out.writeShort(x);
		out.writeShort(y);
		out.writeInt(info);
		if (isWheel()) {
			out.writeInt(rotations);
		}
	}

	public static NetworkMouseControlMessage unmarshall(ObjectInputStream in) throws IOException {
		final int x = in.readShort();
		final int y = in.readShort();
		final int info = in.readInt();
		final int rotations;

		if ((info & WHEEL) == WHEEL) {
			rotations = in.readInt();
		} else {
			rotations = 0;
		}
		return new NetworkMouseControlMessage(x, y, info, rotations);
	}

	public String toString() {
		if (isWheel()) {
			return String.format("[x:%d][y:%d][WHEEL][%d]", x, y, rotations);
		}
		return String.format("[x:%d][y:%d][%s][%s]", x, y, toStringPressed(), toStringButton());
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

	private String toStringButton() {
		if (isButton1()) {
			return "BUTTON1";
		}
		if (isButton2()) {
			return "BUTTON2";
		}
		if (isButton3()) {
			return "BUTTON3";
		}
		return "";
	}
}
