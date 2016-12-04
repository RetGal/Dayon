package mpo.dayon.common.network.message;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public abstract class NetworkMessage
{
    private static final byte MAGIC_NUMBER = (byte) 170;

    protected NetworkMessage()
    {
    }

    public abstract NetworkMessageType getType();

    /**
     * Take into account some extra-info sent over the network with the actual payload ...
     */
    public abstract int getWireSize();

    public abstract void marshall(DataOutputStream out) throws IOException;

    public static void marshallMagicNumber(DataOutputStream out) throws IOException
    {
        out.writeByte(NetworkMessage.MAGIC_NUMBER);
    }

    public static void unmarshallMagicNumber(DataInputStream in) throws IOException
    {
        final int magicNumber = in.readByte();

        if (magicNumber != NetworkMessage.MAGIC_NUMBER)
        {
            if (magicNumber == 0) // possibly the v.0 HELLO message ...
            {
                throw new IOException("Protocol error (possibly using an old version of the assisted)!");
            }
            throw new IOException("Protocol error!");
        }
    }

    public static <T extends Enum<T>> void marshallEnum(DataOutputStream out, Class<T> enumClass, Enum<T> value) throws IOException
    {
        out.write(value.ordinal());
    }

    public static <T extends Enum<T>> T unmarshallEnum(DataInputStream in, Class<T> enumClass) throws IOException
    {
        final byte ordinal = in.readByte();

        final T[] xenums = enumClass.getEnumConstants();

        for (int idx = 0; idx < xenums.length; idx++)
        {
            final T xenum = xenums[idx];

            if (xenum.ordinal() == ordinal)
            {
                return xenum;
            }
        }

        throw new RuntimeException("Unknown " + enumClass.getSimpleName() + " [" + ordinal + "] enum!");
    }

    @Override
    public abstract String toString();
}
