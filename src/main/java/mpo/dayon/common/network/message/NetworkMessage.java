package mpo.dayon.common.network.message;

import java.io.*;
import java.util.Arrays;

public abstract class NetworkMessage {
    private static final byte MAGIC_NUMBER = (byte) 170;

    protected NetworkMessage() {}

    public abstract NetworkMessageType getType();
    public abstract int getWireSize();
    public abstract void marshall(ObjectOutputStream out) throws IOException;

    public static void marshallMagicNumber(ObjectOutputStream out) throws IOException {
        out.writeByte(MAGIC_NUMBER);
    }

    public static void unmarshallMagicNumber(ObjectInputStream in) throws IOException {
        if (MAGIC_NUMBER != in.readByte()) {
            throw new IOException("Protocol error!");
        }
    }

    public static <T extends Enum<T>> void marshallEnum(ObjectOutputStream out, Enum<T> value) throws IOException {
        out.write(value.ordinal());
    }

    public static <T extends Enum<T>> T unmarshallEnum(ObjectInputStream in, Class<T> enumClass) throws IOException {
        byte ordinal = in.readByte();
        return Arrays.stream(enumClass.getEnumConstants())
                .filter(x -> x.ordinal() == ordinal)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown " + enumClass.getSimpleName() + " enum!"));
    }

    @Override
    public abstract String toString();
}