package mpo.dayon.common.capture;

import mpo.dayon.common.gui.common.Position;
import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.util.AbstractMap;

import static org.junit.jupiter.api.Assertions.*;

class CaptureTest {

    private static final Dimension CAPTURE_DIMENSION = new Dimension(4, 4);
    private static final Dimension TILE_DIMENSION = new Dimension(2, 2);

    private static CaptureTile tile(int id, byte... data) {
        return new CaptureTile(id, id, new Position((id % 2) * 2, (id / 2) * 2), 2, 2, data);
    }

    private static Capture capture(CaptureTile... dirty) {
        return new Capture(1, true, 0, 0, CAPTURE_DIMENSION, TILE_DIMENSION, dirty);
    }

    @Test
    void exposesItsValues() {
        // given
        final Capture capture = new Capture(42, true, 3, 2, CAPTURE_DIMENSION, TILE_DIMENSION, new CaptureTile[] { tile(0, (byte) 1, (byte) 1, (byte) 1, (byte) 1) });

        // when, then
        assertEquals(42, capture.getId());
        assertTrue(capture.isReset());
        assertEquals(3, capture.getSkipped());
        assertEquals(2, capture.getMerged());
        assertEquals(4, capture.getWidth());
        assertEquals(4, capture.getHeight());
        assertEquals(2, capture.getTWidth());
        assertEquals(2, capture.getTHeight());
        assertEquals(1, capture.getDirtyTileCount());
    }

    @Test
    void copiesTheDirtyTileArrayOnCreation() {
        // given
        final CaptureTile[] dirty = { tile(0, (byte) 1, (byte) 1, (byte) 1, (byte) 1), null };
        final Capture capture = new Capture(1, false, 0, 0, CAPTURE_DIMENSION, TILE_DIMENSION, dirty);

        // when
        dirty[0] = null;

        // then
        assertNotNull(capture.getDirtyTiles()[0]);
    }

    @Test
    void returnsACopyOfTheDirtyTiles() {
        // given
        final Capture capture = capture(tile(0, (byte) 1, (byte) 1, (byte) 1, (byte) 1));

        // when
        final CaptureTile[] dirty = capture.getDirtyTiles();
        dirty[0] = null;

        // then
        assertNotNull(capture.getDirtyTiles()[0]);
    }

    @Test
    void computesTheCompressionRatio() {
        // given
        final Capture capture = capture(tile(0, (byte) 1, (byte) 2, (byte) 3, (byte) 4), tile(1, (byte) 5, (byte) 6, (byte) 7, (byte) 8));

        // when, then
        assertEquals(4d, capture.computeCompressionRatio(2));
    }

    @Test
    void mergesMissingTilesOfOlderCaptures() {
        // given
        final Capture newer = new Capture(2, false, 1, 0, CAPTURE_DIMENSION, TILE_DIMENSION,
                new CaptureTile[] { tile(0, (byte) 1, (byte) 1, (byte) 1, (byte) 1), null, null, null });
        final Capture olderOne = new Capture(1, false, 2, 1, CAPTURE_DIMENSION, TILE_DIMENSION,
                new CaptureTile[] { null, tile(1, (byte) 2, (byte) 2, (byte) 2, (byte) 2), null, null });
        final Capture olderTwo = new Capture(0, false, 3, 1, CAPTURE_DIMENSION, TILE_DIMENSION,
                new CaptureTile[] { null, null, tile(2, (byte) 3, (byte) 3, (byte) 3, (byte) 3), null });

        // when
        newer.mergeDirtyTiles(new Capture[] { olderOne, olderTwo });

        // then
        final CaptureTile[] merged = newer.getDirtyTiles();
        assertEquals(3, newer.getDirtyTileCount());
        assertNotNull(merged[0]);
        assertNotNull(merged[1]);
        assertNotNull(merged[2]);
        assertNull(merged[3]);
        assertEquals(6, newer.getSkipped());
        assertEquals(3, newer.getMerged());
    }

    @Test
    void keepsTheNewerTilesWhileMerging() {
        // given
        final Capture newer = new Capture(2, false, 0, 0, CAPTURE_DIMENSION, TILE_DIMENSION,
                new CaptureTile[] { tile(0, (byte) 9, (byte) 9, (byte) 9, (byte) 9), null, null, null });
        final Capture older = new Capture(1, false, 0, 0, CAPTURE_DIMENSION, TILE_DIMENSION,
                new CaptureTile[] { tile(0, (byte) 1, (byte) 1, (byte) 1, (byte) 1), null, null, null });

        // when
        newer.mergeDirtyTiles(new Capture[] { older });

        // then
        assertEquals((byte) 9, newer.getDirtyTiles()[0].getCapture().getByte(0));
    }

    @Test
    void doesNotMergeCapturesWithADifferentTileCount() {
        // given
        final Capture newer = new Capture(2, false, 0, 0, CAPTURE_DIMENSION, TILE_DIMENSION,
                new CaptureTile[] { tile(0, (byte) 1, (byte) 1, (byte) 1, (byte) 1), null, null, null });
        final Capture older = new Capture(1, false, 5, 4, new Dimension(2, 2), TILE_DIMENSION, new CaptureTile[] { tile(0, (byte) 2, (byte) 2, (byte) 2, (byte) 2) });

        // when
        newer.mergeDirtyTiles(new Capture[] { older });

        // then
        assertEquals(1, newer.getDirtyTileCount());
        assertEquals(5, newer.getSkipped());
        assertEquals(5, newer.getMerged());
    }

    @Test
    void createsAGrayBufferedImage() {
        // given
        final Capture capture = capture(
                tile(0, (byte) 1, (byte) 1, (byte) 1, (byte) 1),
                tile(1, (byte) 2, (byte) 2, (byte) 2, (byte) 2),
                tile(2, (byte) 3, (byte) 3, (byte) 3, (byte) 3),
                tile(3, (byte) 4, (byte) 4, (byte) 4, (byte) 4));

        // when
        final AbstractMap.SimpleEntry<BufferedImage, byte[]> entry = capture.createBufferedImage(null, 0, 0);

        // then
        assertArrayEquals(new byte[] { 1, 1, 2, 2, 1, 1, 2, 2, 3, 3, 4, 4, 3, 3, 4, 4 }, entry.getValue());
        assertEquals(4, entry.getKey().getWidth());
        assertEquals(4, entry.getKey().getHeight());
        assertEquals(1, entry.getKey().getColorModel().getNumComponents());
    }

    @Test
    void createsAColorBufferedImage() {
        // given
        final byte[] pixelData = new byte[16];
        for (int idx = 0; idx < pixelData.length; idx++) {
            pixelData[idx] = (byte) (idx + 1);
        }
        final Capture capture = new Capture(1, true, 0, 0, new Dimension(2, 2), new Dimension(2, 2), new CaptureTile[] { tile(0, pixelData) });

        // when
        final AbstractMap.SimpleEntry<BufferedImage, byte[]> entry = capture.createBufferedImage(null, 0, 0);

        // then
        assertArrayEquals(pixelData, entry.getValue());
        assertEquals(2, entry.getKey().getWidth());
        assertEquals(2, entry.getKey().getHeight());
        assertEquals(3, entry.getKey().getColorModel().getNumComponents());
    }

    @Test
    void reusesThePreviousBufferWhenTheDimensionMatches() {
        // given
        final Capture capture = capture(tile(0, (byte) 1, (byte) 1, (byte) 1, (byte) 1), tile(1, (byte) 1, (byte) 1, (byte) 1, (byte) 1),
                tile(2, (byte) 1, (byte) 1, (byte) 1, (byte) 1), tile(3, (byte) 1, (byte) 1, (byte) 1, (byte) 1));
        final byte[] previous = capture.createBufferedImage(null, 0, 0).getValue();

        // when
        final byte[] reused = capture.createBufferedImage(previous, 4, 4).getValue();
        final byte[] notReused = capture.createBufferedImage(previous, 8, 8).getValue();
        final byte[] wrongSize = capture.createBufferedImage(new byte[3], 4, 4).getValue();

        // then
        assertSame(previous, reused);
        assertNotSame(previous, notReused);
        assertNotSame(previous, wrongSize);
        assertEquals(16, notReused.length);
        assertEquals(16, wrongSize.length);
    }
}
