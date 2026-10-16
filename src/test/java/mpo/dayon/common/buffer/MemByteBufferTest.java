package mpo.dayon.common.buffer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
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
    void write() {
        final int val = 234;
        buffer.write(val);
        assertEquals(1, buffer.size());
        assertEquals((byte) val, buffer.getInternal()[0]);
    }

    @Test
    void writes() {
        final int val = 234;
        buffer.writes(val, val+1, val+2);
        assertEquals(3, buffer.size());
        assertEquals((byte) val, buffer.getInternal()[0]);
        assertEquals((byte) val+1, buffer.getInternal()[1]);
        assertEquals((byte) val+2, buffer.getInternal()[2]);
    }

    @Test
    void writeInt() {
        buffer.writeInt(BEAST);
        assertEquals(4, buffer.size());
    }

    @Test
    void writeShorts() {
        buffer.writeShorts(BEAST);
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
        int size = 42;
        buffer.fill(size, BEAST);
        try(MemByteBuffer memBuffer = new MemByteBuffer(buffer.getInternal())) {
            assertEquals(size, memBuffer.size());
            assertEquals(buffer.getInternal().length, memBuffer.getInternal().length);
        }
    }

    @Test
    void streamsContentsWithoutChangingSnapshotBehavior() throws IOException {
        buffer.writes(1, 2, 3);
        byte[] snapshot = buffer.getInternal();
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        buffer.writeTo(output);

        assertArrayEquals(new byte[] { 1, 2, 3 }, output.toByteArray());
        assertArrayEquals(new byte[] { 1, 2, 3 }, buffer.asInputStream().readAllBytes());
        snapshot[0] = 9;
        assertEquals(1, buffer.getInternal()[0]);
    }

    @Test
    void appendsAndCopiesContentsWithoutExposingInternalStorage() {
        buffer.writes(1, 2, 3);
        MemByteBuffer destination = new MemByteBuffer();
        byte[] copied = new byte[2];

        buffer.appendTo(destination);
        buffer.copyTo(copied, 0, 1, 2);

        assertArrayEquals(new byte[] { 1, 2, 3 }, destination.getInternal());
        assertArrayEquals(new byte[] { 2, 3 }, copied);
        assertArrayEquals(new byte[] { 1, 2, 3 }, buffer.getInternal());
    }
}