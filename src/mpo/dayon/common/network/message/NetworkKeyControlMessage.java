package mpo.dayon.common.network.message;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * From the assistant to the assisted.
 */
public class NetworkKeyControlMessage extends NetworkMessage
{
    public enum KeyState
    {
        PRESSED,
        RELEASED,
    }

    public static final int PRESSED = 1;

    public static final int RELEASED = 1 << 1;

    private final int info;

    private final int keycode;

    public NetworkKeyControlMessage(KeyState buttonState, int keycode)
    {
        this(buttonState == KeyState.PRESSED ? PRESSED : RELEASED, keycode);
    }

    private NetworkKeyControlMessage(int info, int keycode)
    {
        this.info = info;
        this.keycode = keycode;
    }

    public NetworkMessageType getType()
    {
        return NetworkMessageType.KEY_CONTROL;
    }

    public int getKeyCode()
    {
        return keycode;
    }

    public boolean isPressed()
    {
        return (info & PRESSED) == PRESSED;
    }

    public boolean isReleased()
    {
        return (info & RELEASED) == RELEASED;
    }

    public int getWireSize()
    {
        return 9; // type (byte) + info (int) + keycode (int)
    }

    public void marshall(DataOutputStream out) throws IOException
    {
        marshallEnum(out, NetworkMessageType.class, getType());

        out.writeInt(info);
        out.writeInt(keycode);
    }

    public static NetworkKeyControlMessage unmarshall(DataInputStream in) throws IOException
    {
        final int info = in.readInt();
        final int keycode = in.readInt();

        return new NetworkKeyControlMessage(info, keycode);
    }

    public String toString()
    {
        return String.format("[%s] [%d]", toStringPressed(), keycode);
    }

    private String toStringPressed()
    {
        if (isPressed())
        {
            return "PRESSED";
        }

        if (isReleased())
        {
            return "RELEASED";
        }

        return "";
    }

}