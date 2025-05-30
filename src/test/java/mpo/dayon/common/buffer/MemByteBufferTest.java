package mpo.dayon.common.buffer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class MemByteBufferTest {

    private static final int BEAST = 666;
    private static final int DEFAULT_CAPACITY = 32;
    private MemByteBuffer buffer;

    @BeforeEach
    void setUp() {
        buffer = MemByteBuffer.acquire();
    }

    @AfterEach
    void tearDown() throws IOException {
        buffer.close();
    }

    @Test
    void write() {
        final int val = 234;
        buffer.write(val);
        assertEquals(1, buffer.size());
        assertEquals((byte) val, buffer.getInternal()[0]);
        assertEquals(DEFAULT_CAPACITY, buffer.capacity());
    }

    @Test
    void writeInt() {
        buffer.writeInt(BEAST);
        assertEquals(4, buffer.size());
        assertEquals(DEFAULT_CAPACITY, buffer.capacity());
        buffer.writeInt(BEAST);
        buffer.writeInt(BEAST);
        buffer.writeInt(BEAST);
        buffer.writeInt(BEAST);
        buffer.writeInt(BEAST);
        buffer.writeInt(BEAST);
        buffer.writeInt(BEAST);
        buffer.writeInt(BEAST);
        assertEquals(36, buffer.size());
        assertEquals(DEFAULT_CAPACITY*2, buffer.capacity());
    }

    @Test
    void writeShort() {
        buffer.writeShort(BEAST);
        assertEquals(2, buffer.size());
    }

    @Test
    void writeByteArray() {
        buffer.write(new byte[BEAST]);
        assertEquals(BEAST, buffer.size());
    }

    @Test
    void fill() {
        buffer.fill(3, BEAST);
        assertEquals(3, buffer.size());
    }

    @Test
    void writeLenAsShort() {
        final int mark = 10;
        buffer.fill(12, 8);
        assertEquals(8, buffer.getInternal()[mark]);
        buffer.writeLenAsShort(mark);
        assertEquals(0, buffer.getInternal()[mark]);
        assertEquals(12, buffer.size());
        assertEquals(DEFAULT_CAPACITY, buffer.capacity());
    }

    @Test
    void copyConstructor() {
        int size = 42;
        buffer.fill(size, BEAST);
        MemByteBuffer memBuffer = MemByteBuffer.acquire(buffer.getInternal());
        assertEquals(size, memBuffer.size());
        assertEquals(buffer.getInternal().length, memBuffer.getInternal().length);
    }
}