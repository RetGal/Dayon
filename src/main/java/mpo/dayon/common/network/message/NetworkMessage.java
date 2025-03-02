package mpo.dayon.common.network.message;

import java.io.*;

public abstract class NetworkMessage {
    private static final byte MAGIC_NUMBER = (byte) 170;

    protected NetworkMessage() {}

    public abstract NetworkMessageType getType();
    public abstract int getWireSize();
    public abstract void marshall(ObjectOutputStream out) throws IOException;

    public static void marshallMagicNumber(DataOutput out) throws IOException {
        out.writeByte(MAGIC_NUMBER);
    }

    public static void unmarshallMagicNumber(DataInput in) throws IOException {
        if (in.readByte() != MAGIC_NUMBER) {
            throw new IOException("Protocol error!");
        }
    }

    public static <T extends Enum<T>> void marshallEnum(DataOutput out, Enum<T> value) throws IOException {
        out.writeByte(value.ordinal());
    }

    public static <T extends Enum<T>> T unmarshallEnum(ObjectInputStream in, Class<T> enumClass) throws IOException {
        int ordinal = in.readByte();
        T[] enumConstants = enumClass.getEnumConstants();
        if (ordinal < 0 || ordinal >= enumConstants.length) {
            throw new IllegalArgumentException("Unknown " + enumClass.getSimpleName() + " enum!");
        }
        return enumConstants[ordinal];
    }

    @Override
    public abstract String toString();
}