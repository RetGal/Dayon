package mpo.dayon.common.squeeze;

import mpo.dayon.common.buffer.MemByteBuffer;
import mpo.dayon.common.capture.Capture;
import mpo.dayon.common.capture.CaptureTile;
import mpo.dayon.common.gui.common.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.awt.Dimension;
import java.io.IOException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class CompressorTest {

    private static final int CAPTURE_WIDTH = 4;
    private static final int CAPTURE_HEIGHT = 4;
    private static final int TILE_WIDTH = 2;
    private static final int TILE_HEIGHT = 2;
    private static final int TILE_SIZE = TILE_WIDTH * TILE_HEIGHT;

    private static CaptureTile tile(int id, byte... data) {
        return new CaptureTile(id, id, new Position((id % 2) * TILE_WIDTH, (id / 2) * TILE_HEIGHT), TILE_WIDTH, TILE_HEIGHT, data);
    }

    private static CaptureTile singleLevelTile(int id, byte level) {
        final byte[] data = new byte[TILE_SIZE];
        Arrays.fill(data, level);
        return tile(id, data);
    }

    private static CaptureTile multiLevelTile(int id) {
        // the checksum is used as cache id - it has to match the one computed while decompressing
        final byte[] data = { 1, 2, 3, 4 };
        return new CaptureTile(id, CaptureTile.computeChecksum(data, 0, data.length), new Position((id % 2) * TILE_WIDTH, (id / 2) * TILE_HEIGHT), TILE_WIDTH, TILE_HEIGHT, data);
    }

    private static Capture capture(boolean reset, CaptureTile... dirty) {
        // the wire format does not carry the skipped count - it is overwritten by the merged count
        return new Capture(42, reset, 2, 2, new Dimension(CAPTURE_WIDTH, CAPTURE_HEIGHT), new Dimension(TILE_WIDTH, TILE_HEIGHT), dirty);
    }

    private static Capture singleLevelCapture(boolean reset) {
        return capture(reset, singleLevelTile(0, (byte) 1), singleLevelTile(1, (byte) 2), singleLevelTile(2, (byte) 3), singleLevelTile(3, (byte) 4));
    }

    @ParameterizedTest
    @EnumSource(CompressionMethod.class)
    void roundTripsSingleLevelTiles(CompressionMethod method) throws IOException {
        // given
        final Compressor compressor = Compressor.get(method);
        final Capture capture = singleLevelCapture(true);
        final CaptureTile[] original = capture.getDirtyTiles();

        // when
        final MemByteBuffer compressed = compressor.compress(new NullTileCache(), capture);
        final Capture decompressed = compressor.decompress(new NullTileCache(), compressed);

        // then
        assertEquals(42, decompressed.getId());
        assertTrue(decompressed.isReset());
        assertEquals(2, decompressed.getSkipped());
        assertEquals(2, decompressed.getMerged());
        assertEquals(CAPTURE_WIDTH, decompressed.getWidth());
        assertEquals(CAPTURE_HEIGHT, decompressed.getHeight());
        assertEquals(TILE_WIDTH, decompressed.getTWidth());
        assertEquals(TILE_HEIGHT, decompressed.getTHeight());
        assertEquals(4, decompressed.getDirtyTileCount());

        final CaptureTile[] restored = decompressed.getDirtyTiles();
        for (int idx = 0; idx < original.length; idx++) {
            assertEquals(idx, restored[idx].getId());
            assertArrayEquals(original[idx].getCapture().getInternal(), restored[idx].getCapture().getInternal());
            assertEquals(original[idx].getSingleLevel(), restored[idx].getSingleLevel());
        }
    }

    @ParameterizedTest
    @EnumSource(CompressionMethod.class)
    void roundTripsMultiLevelTiles(CompressionMethod method) throws IOException {
        // given
        final Compressor compressor = Compressor.get(method);
        final CaptureTile multiLevel = tile(0, (byte) 5, (byte) 6, (byte) 7, (byte) 8);
        final Capture capture = new Capture(7, false, 0, 0, new Dimension(2, 2), new Dimension(2, 2), new CaptureTile[] { multiLevel });

        // when
        final MemByteBuffer compressed = compressor.compress(new NullTileCache(), capture);
        final Capture decompressed = compressor.decompress(new NullTileCache(), compressed);

        // then
        assertFalse(decompressed.isReset());
        assertEquals(1, decompressed.getDirtyTileCount());
        assertArrayEquals(new byte[] { 5, 6, 7, 8 }, decompressed.getDirtyTiles()[0].getCapture().getInternal());
        assertEquals(-1, decompressed.getDirtyTiles()[0].getSingleLevel());
    }

    @ParameterizedTest
    @EnumSource(CompressionMethod.class)
    void roundTripsSkippedTiles(CompressionMethod method) throws IOException {
        // given
        final Compressor compressor = Compressor.get(method);
        final Capture capture = capture(false, singleLevelTile(0, (byte) 1), null, null, singleLevelTile(3, (byte) 4));

        // when
        final MemByteBuffer compressed = compressor.compress(new NullTileCache(), capture);
        final Capture decompressed = compressor.decompress(new NullTileCache(), compressed);

        // then
        final CaptureTile[] restored = decompressed.getDirtyTiles();
        assertEquals(4, restored.length);
        assertEquals(2, decompressed.getDirtyTileCount());
        assertEquals(1, restored[0].getSingleLevel());
        assertNull(restored[1]);
        assertNull(restored[2]);
        assertEquals(4, restored[3].getSingleLevel());
    }

    @Test
    void roundTripsMoreThanOneMarkerChunk() throws IOException {
        // given
        final Compressor compressor = Compressor.get(CompressionMethod.ZIP);
        final int tileCount = 256;
        final CaptureTile[] dirty = new CaptureTile[tileCount];
        for (int idx = 0; idx < tileCount; idx++) {
            dirty[idx] = new CaptureTile(idx, idx, new Position(idx, idx), 1, 1, new byte[] { (byte) idx });
        }
        final Capture capture = new Capture(1, false, 0, 0, new Dimension(tileCount, 1), new Dimension(1, 1), dirty);

        // when
        final MemByteBuffer compressed = compressor.compress(new NullTileCache(), capture);
        final Capture decompressed = compressor.decompress(new NullTileCache(), compressed);

        // then
        assertEquals(tileCount, decompressed.getDirtyTileCount());
        for (int idx = 0; idx < tileCount; idx++) {
            assertEquals((byte) idx, decompressed.getDirtyTiles()[idx].getCapture().getByte(0));
        }
    }

    @Test
    void roundTripsManySkippedTiles() throws IOException {
        // given
        final Compressor compressor = Compressor.get(CompressionMethod.ZIP);
        final int tileCount = 256;
        final Capture capture = new Capture(1, false, 0, 0, new Dimension(tileCount, 1), new Dimension(1, 1), new CaptureTile[tileCount]);

        // when
        final MemByteBuffer compressed = compressor.compress(new NullTileCache(), capture);
        final Capture decompressed = compressor.decompress(new NullTileCache(), compressed);

        // then
        assertEquals(0, decompressed.getDirtyTileCount());
    }

    @Test
    void reusesCachedTilesOverConsecutiveCaptures() throws IOException {
        // given
        final Compressor compressor = Compressor.get(CompressionMethod.ZIP);
        final Capture capture = new Capture(1, false, 0, 0, new Dimension(2, 2), new Dimension(2, 2), new CaptureTile[] { multiLevelTile(0) });
        final RegularTileCache encodingCache = new RegularTileCache(10, 5);
        final RegularTileCache decodingCache = new RegularTileCache(10, 5);

        // when
        final MemByteBuffer firstCompressed = compressor.compress(encodingCache, capture);
        final Capture firstDecompressed = compressor.decompress(decodingCache, firstCompressed);
        final int encodingHitsAfterFirstCapture = encodingCache.getHits();
        final int decodingHitsAfterFirstCapture = decodingCache.getHits();
        final MemByteBuffer secondCompressed = compressor.compress(encodingCache, capture);
        final Capture secondDecompressed = compressor.decompress(decodingCache, secondCompressed);

        // then
        assertEquals(0, encodingHitsAfterFirstCapture);
        assertEquals(0, decodingHitsAfterFirstCapture);
        assertEquals(1, encodingCache.getHits(), "the second capture references the cached tile");
        assertEquals(1, decodingCache.getHits(), "the second capture reuses the cached tile");
        assertArrayEquals(firstDecompressed.getDirtyTiles()[0].getCapture().getInternal(), secondDecompressed.getDirtyTiles()[0].getCapture().getInternal());
    }

    @Test
    void clearsTheCacheOnResetCaptures() throws IOException {
        // given
        final Compressor compressor = Compressor.get(CompressionMethod.ZIP);
        final RegularTileCache cache = new RegularTileCache(10, 5);
        cache.add(multiLevelTile(0));
        assertEquals(1, cache.size());

        // when
        compressor.compress(cache, singleLevelCapture(true));

        // then
        assertEquals(0, cache.size(), "single level tiles are not cached and the reset clears the cache");
    }

    @Test
    void keepsTheCacheOnIncrementalCaptures() throws IOException {
        // given
        final Compressor compressor = Compressor.get(CompressionMethod.ZIP);
        final RegularTileCache cache = new RegularTileCache(10, 5);
        cache.add(multiLevelTile(3));
        assertEquals(1, cache.size());

        // when
        compressor.compress(cache, singleLevelCapture(false));

        // then
        assertEquals(1, cache.size(), "single level tiles are not cached but the existing ones are kept");
    }

    @Test
    void decompressesUnknownCacheReferencesToABlackTile() throws IOException {
        // given
        final Compressor compressor = Compressor.get(CompressionMethod.ZIP);
        final Capture capture = new Capture(1, false, 0, 0, new Dimension(2, 2), new Dimension(2, 2), new CaptureTile[] { multiLevelTile(0) });
        final RegularTileCache cache = new RegularTileCache(10, 5);
        compressor.compress(cache, capture);
        // the second capture only references the tile of the first one
        final MemByteBuffer compressed = compressor.compress(cache, capture);

        // when
        final Capture decompressed = compressor.decompress(new NullTileCache(), compressed);

        // then
        final CaptureTile restored = decompressed.getDirtyTiles()[0];
        assertTrue(restored.isFromCache());
        assertArrayEquals(new byte[TILE_SIZE], restored.getCapture().getInternal());
    }

    @Test
    void getReturnsOneCompressorPerMethod() {
        // when, then
        assertSame(Compressor.get(CompressionMethod.ZIP), Compressor.get(CompressionMethod.ZIP));
        assertSame(Compressor.get(CompressionMethod.XZ), Compressor.get(CompressionMethod.XZ));
        assertSame(Compressor.get(CompressionMethod.NONE), Compressor.get(CompressionMethod.NONE));
        assertEquals(CompressionMethod.ZIP, Compressor.get(CompressionMethod.ZIP).getMethod());
        assertEquals(CompressionMethod.XZ, Compressor.get(CompressionMethod.XZ).getMethod());
        assertEquals(CompressionMethod.NONE, Compressor.get(CompressionMethod.NONE).getMethod());
    }
}
