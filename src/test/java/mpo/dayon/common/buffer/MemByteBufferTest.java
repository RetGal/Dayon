package mpo.dayon.common.buffer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class MemByteBufferTest {

    private static final int BEAST = 666;
    private MemByteBuffer buffer;

    @BeforeEach
    void setUp() {
        buffer = new MemByteBuffer();
    }

    @AfterEach
    void tearDown() throws IOException {
        buffer.close();
    }

    @Test
    void writeInt() {
        buffer.writeInt(BEAST);
        assertEquals(4, buffer.size());
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
    }

    @Test
    void copyConstructor() throws IOException {
        buffer.fill(33, BEAST);
        try(MemByteBuffer memBuffer = new MemByteBuffer(buffer.getInternal())) {
            assertEquals(64, memBuffer.size());
            assertEquals(buffer.getInternal().length, memBuffer.getInternal().length);
        }
    }
}