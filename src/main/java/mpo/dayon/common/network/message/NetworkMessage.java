package mpo.dayon.common.network.message;

import java.io.*;
import java.util.Arrays;

public abstract class NetworkMessage {
    private static final byte MAGIC_NUMBER = (byte) 170;

    NetworkMessage() {
    }

    public abstract NetworkMessageType getType();

    /**
     * Take into account some extra-info sent over the network with the actual
     * payload ...
     */
    public abstract int getWireSize();

    public abstract void marshall(ObjectOutputStream out) throws IOException;

    public static void marshallMagicNumber(ObjectOutputStream out) throws IOException {
        out.writeByte(NetworkMessage.MAGIC_NUMBER);
    }

    public static void unmarshallMagicNumber(ObjectInputStream in) throws IOException {
        if (NetworkMessage.MAGIC_NUMBER != in.readByte()) {
            throw new IOException("Protocol error!");
        }
    }

    static <T extends Enum<T>> void marshallEnum(ObjectOutputStream out, Enum<T> value) throws IOException {
        out.write(value.ordinal());
    }

    public static <T extends Enum<T>> T unmarshallEnum(ObjectInputStream in, Class<T> enumClass) throws IOException {
        final byte ordinal = in.readByte();
        final T[] xenums = enumClass.getEnumConstants();
        return Arrays.stream(xenums)
                .filter(xenum -> xenum.ordinal() == ordinal)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown " + enumClass.getSimpleName() + " [" + ordinal + "] enum!"));
    }

    @Override
    public abstract String toString();
}
