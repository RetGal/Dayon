package mpo.dayon.common.capture;

import mpo.dayon.common.buffer.MemByteBuffer;
import mpo.dayon.common.gui.common.Position;
import org.junit.jupiter.api.Test;

import java.util.zip.Adler32;
import java.util.zip.Checksum;

import static org.junit.jupiter.api.Assertions.*;

class CaptureTileTest {

    private static final byte[] GRAY_BYTES = new byte[]{(byte) 200, (byte) 200, (byte) 200, (byte) 200};

    private static final byte[] COLOR_BYTES = new byte[]{1, 2, 3, 4};

    @Test
    void computeSingleLevel() {
        // given, when
        CaptureTile gray = new CaptureTile(0, 1, new Position(0, 0), 2, 2, GRAY_BYTES);
        CaptureTile color = new CaptureTile(0, 1, new Position(0, 0), 2, 2, COLOR_BYTES);

        // then
        assertEquals((byte) 200, gray.getSingleLevel());
        assertEquals(-1, color.getSingleLevel(), "a multilevel tile should return -1");
    }

    @Test
    void missingTileProperties() {
        // given, when
        CaptureTile missing = CaptureTile.MISSING;

        // then
        assertEquals(-1, missing.getId());
        assertEquals(-1, missing.getX());
        assertEquals(-1, missing.getY());
        assertEquals(-1, missing.getWidth());
        assertEquals(-1, missing.getHeight());
        assertEquals(-1, missing.getSingleLevel());
        assertFalse(missing.isFromCache());
        assertNull(missing.getCapture());
    }

    @Test
    void computeChecksumMatchesAdler32() {
        // given
        long expected;
        Checksum checksum = new Adler32();
        checksum.update(COLOR_BYTES, 1, 2);

        // when
        expected = checksum.getValue();

        // then
        assertEquals(expected, CaptureTile.computeChecksum(COLOR_BYTES, 1, 2));
    }

    @Test
    void decompressedTileComputesChecksum() {
        // given
        CaptureTile.XYWH xywh = new CaptureTile.XYWH(0, 0, 2, 2); // package-private ctor, same package
        MemByteBuffer buffer = new MemByteBuffer(COLOR_BYTES);

        // when
        CaptureTile tile = new CaptureTile(7, xywh, buffer);

        // then
        assertEquals(7, tile.getId());
        assertEquals(CaptureTile.computeChecksum(COLOR_BYTES, 0, COLOR_BYTES.length), tile.getChecksum());
        assertEquals(0, tile.getX());
        assertEquals(0, tile.getY());
        assertEquals(2, tile.getWidth());
        assertEquals(2, tile.getHeight());
        assertEquals(-1, tile.getSingleLevel());
        assertFalse(tile.isFromCache());
        assertSame(buffer, tile.getCapture());
    }

    @Test
    void decompressedSingleLevelTileFillsCapture() {
        // given
        CaptureTile.XYWH xywh = new CaptureTile.XYWH(32, 64, 3, 2);

        // when
        CaptureTile tile = new CaptureTile(5, xywh, (byte) 42);

        // then
        assertEquals(42, tile.getSingleLevel());
        assertEquals(32, tile.getX());
        assertEquals(64, tile.getY());
        assertEquals(3, tile.getWidth());
        assertEquals(2, tile.getHeight());
        assertEquals(-1, tile.getChecksum());
        byte[] filled = tile.getCapture().getInternal();
        assertEquals(3 * 2, filled.length);
        for (byte b : filled) {
            assertEquals((byte) 42, b);
        }
    }

    @Test
    void tileFromCacheSharesCachedCapture() {
        // given
        CaptureTile cached = new CaptureTile(1, 2, new Position(0, 0), 2, 2, COLOR_BYTES);
        CaptureTile.XYWH xywh = new CaptureTile.XYWH(16, 16, 2, 2);

        // when
        CaptureTile tile = new CaptureTile(9, xywh, cached);

        // then
        assertTrue(tile.isFromCache());
        assertEquals(9, tile.getId());
        assertEquals(16, tile.getX());
        assertEquals(16, tile.getY());
        assertSame(cached.getCapture(), tile.getCapture(), "capture buffer should be shared");
    }

    @Test
    void tileFromMissingCacheEntryGetsBlackCapture() {
        // given
        CaptureTile.XYWH xywh = new CaptureTile.XYWH(8, 8, 4, 4);

        // when
        CaptureTile tile = new CaptureTile(10, xywh, CaptureTile.MISSING);

        // then
        assertTrue(tile.isFromCache());
        byte[] filled = tile.getCapture().getInternal();
        assertEquals(4 * 4, filled.length);
        for (byte b : filled) {
            assertEquals((byte) 0, b, "black image");
        }
    }

    @Test
    void referenceCountingNeverGoesNegative() {
        // given
        CaptureTile tile = new CaptureTile(1, 2, new Position(0, 0), 2, 2, COLOR_BYTES);
        assertEquals(0, tile.getReferenceCount());

        // when
        tile.decrementReferenceCount();
        tile.incrementReferenceCount();
        tile.incrementReferenceCount();
        tile.decrementReferenceCount();

        // then
        assertEquals(1, tile.getReferenceCount());
    }

    @Test
    void xywhEquality() {
        // given, when
        CaptureTile.XYWH xywh = new CaptureTile.XYWH(10, 20, 32, 32);

        // then
        assertTrue(xywh.equals(10, 20, 32, 32));
        assertFalse(xywh.equals(11, 20, 32, 32));
        assertFalse(xywh.equals(10, 21, 32, 32));
        assertFalse(xywh.equals(10, 20, 33, 32));
        assertFalse(xywh.equals(10, 20, 32, 33));
    }

    @Test
    void getXYWHTilesTheCapture() {
        // when
        CaptureTile.XYWH[] tiles = CaptureTile.getXYWH(100, 75, 32, 32);

        // then
        assertEquals(12, tiles.length);
        assertTrue(tiles[0].equals(0, 0, 32, 32));
        assertTrue(tiles[3].equals(96, 0, 4, 32), "last tile along the X axis is narrowed");
        assertTrue(tiles[11].equals(96, 64, 4, 11), "last tile along both axes is narrowed");
    }

    @Test
    void getXYWHIsCachedBetweenCalls() {
        // when
        CaptureTile.XYWH[] first = CaptureTile.getXYWH(64, 64, 32, 32);
        CaptureTile.XYWH[] second = CaptureTile.getXYWH(64, 64, 32, 32);

        // then
        assertEquals(4, first.length);
        assertEquals(first.length, second.length);
        for (int idx = 0; idx < first.length; idx++) {
            assertTrue(second[idx].equals(first[idx].x, first[idx].y, first[idx].w, first[idx].h));
        }
    }
}
