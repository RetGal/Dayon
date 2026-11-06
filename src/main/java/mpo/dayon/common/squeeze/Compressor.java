package mpo.dayon.common.squeeze;

import java.awt.*;
import java.io.DataInputStream;
import java.io.IOException;

import mpo.dayon.common.buffer.MemByteBuffer;
import mpo.dayon.common.capture.Capture;
import mpo.dayon.common.capture.CaptureTile;
import mpo.dayon.common.log.Log;

import static mpo.dayon.common.utils.ValidationUtilities.*;

public final class Compressor {
    /**
     * NONE. (testing only)
     */
    private static final Compressor NULL_COMPRESSOR = new Compressor(CompressionMethod.NONE, new NullRunLengthEncoder(), new NullZipper());

    /**
     * ZIP (with regular run-length-encoding).
     */
    private static final Compressor ZIP_COMPRESSOR = new Compressor(CompressionMethod.ZIP, new RegularRunLengthEncoder(), new ZipZipper());

    /**
     * XZ.
     */
    private static final Compressor XZ_COMPRESSOR = new Compressor(CompressionMethod.XZ, new NullRunLengthEncoder(), new XzZipper());

    private final CompressionMethod method;

    private final RunLengthEncoder rle;

    private final Zipper zipper;

    private Compressor(CompressionMethod method, RunLengthEncoder rle, Zipper zipper) {
        this.method = method;
        this.rle = rle;
        this.zipper = zipper;
    }

    public static Compressor get(CompressionMethod method) {

        switch (method) {
            case ZIP:
                return ZIP_COMPRESSOR;
            case XZ:
                return XZ_COMPRESSOR;
            case NONE:
                return NULL_COMPRESSOR;
            default:
                throw new IllegalArgumentException("Unsupported compressor configuration [" + method + "]!");
        }

    }

    public CompressionMethod getMethod() {
        return method;
    }

    public MemByteBuffer compress(TileCache cache, Capture capture) throws IOException {
        final MemByteBuffer encoded = new MemByteBuffer();
        encoded.writeInt(capture.getId());
        encoded.write(capture.isReset() ? 1 : 0);
        encoded.writes(capture.getMerged(), capture.getMerged()); // each as a byte (!)
        encoded.writeShorts(capture.getWidth(), capture.getHeight(), capture.getTWidth(), capture.getTHeight());
        if (capture.isReset()) {
            Log.debug("Clear compressor cache [capture:" + capture.getId() + "]");
            cache.clear(); // here for symmetry with the de-compressor (!)
        }
        final CaptureTile[] tiles = capture.getDirtyTiles();
        int idx = 0;
        final int tilesLength = tiles.length;
        while (idx < tilesLength) {
            final int markerCount = computeMarkerCount(tiles, idx);
            encoded.write(markerCount);
            if (markerCount > 0) {
                // non-null tile(s) count
                for (int tidx = idx; tidx < idx + markerCount; tidx++) {
                    encodeTile(cache, rle, encoded, tiles[tidx]);
                }
                idx += markerCount;
            } else {
                // null tile(s) count
                idx += (-markerCount + 1);
            }
        }
        return zipper.zip(encoded);
    }

    /**
     * <pre>
     * [    1 .. 127 ] : N non null tiles
     * [ -128 .. 0   ] : (-N+1) null tiles
     * </pre>
     */
    private static int computeMarkerCount(CaptureTile[] tiles, int from) {
        final CaptureTile tile = tiles[from++];
        final int tilesLength = tiles.length;
        if (tile == null) {
            int count = 0;
            while (count < 128 && from < tilesLength && tiles[from++] == null) {
                ++count;
            }
            return -count;
        }
        int count = 1;
        while (count < 127 && from < tilesLength && tiles[from++] != null) {
            ++count;
        }
        return count;
    }

    private static void encodeTile(TileCache cache, RunLengthEncoder encoder, MemByteBuffer encoded, CaptureTile tile) {
        // single-level tile : [ 0 .. 256 ]
        if (tile.getSingleLevel() != -1) {
            encoded.writeShorts(tile.getSingleLevel() & 0xFF);
            return;
        }
        // multi-level tile : cached [256]
        final int cacheId = cache.getCacheId(tile);
        if (cache.get(cacheId) != CaptureTile.MISSING) // LRU usage (!)
        {
            encoded.writeShorts(256);
            encoded.writeInt(cacheId);
            return;
        }
        // multi-level tile (not-cached) [ -32768 .. 0 ]
        final int mark = encoded.mark();
        encoded.writeShorts(42); // dunno yet (!)
        encoder.runLengthEncode(encoded, tile.getCapture());
        encoded.writeLenAsShort(mark);
        cache.add(tile);
    }

    public Capture decompress(TileCache cache, MemByteBuffer zipped) throws IOException {
        final MemByteBuffer unzipped = zipper.unzip(zipped);
        final DataInputStream in = new DataInputStream(unzipped.asInputStream());
        final int cId = in.readInt();
        final boolean cReset = in.read() == 1;
        if (cReset) {
            Log.debug("Clear de-compressor cache [capture:" + cId + "]");
            cache.clear();
        }
        final int cSkipped = in.readUnsignedByte();
        final int cMerged = in.readUnsignedByte();
        final int captureWidth = requireRange("capture width", in.readShort(), MIN_VALUE, MAX_WIDTH);
        final int captureHeight = requireRange("capture height", in.readShort(), MIN_VALUE, MAX_HEIGHT);
        final int tileWidth = requireRange("tile width", in.readShort(), MIN_VALUE, MAX_WIDTH);
        final int tileHeight = requireRange("tile height", in.readShort(), MIN_VALUE, MAX_HEIGHT);
        final long tileCount = CaptureTile.getTileCount(captureWidth, captureHeight, tileWidth, tileHeight);
        validateTileCount(tileCount);
        final Dimension captureDimension = new Dimension(captureWidth, captureHeight);
        final Dimension tileDimension = new Dimension(tileWidth, tileHeight);
        final CaptureTile.XYWH[] xywh = CaptureTile.getXYWH(captureWidth, captureHeight, tileWidth, tileHeight);
        final CaptureTile[] dirty = new CaptureTile[xywh.length];
        int idx = 0;
        final int dirtyLen = dirty.length;
        while (idx < dirtyLen) {
            final int markerCount = in.readByte();
            if (markerCount > 0) // non-null tile(s)
            {
                final int endIdx = idx + markerCount;
                for (int tidx = idx; tidx < endIdx; tidx++) {
                    final int value = in.readShort();
                    if (value >= 0 && value < 256) // single-level (most common case)
                    {
                        dirty[tidx] = new CaptureTile(tidx, xywh[tidx], (byte) value);
                    } else if (value == 256) // multi-level (cached)
                    {
                        dirty[tidx] = new CaptureTile(tidx, xywh[tidx], cache.get(in.readInt()));
                    } else // multi-level (not cached)
                    {
                        processUncached(cache, in, xywh[tidx], dirty, tidx, value);
                    }
                }
                idx = endIdx;
            } else // null tile(s)
            {
                idx += (-markerCount + 1);
            }
        }
        return new Capture(cId, cReset, cSkipped, cMerged, captureDimension, tileDimension, dirty);
    }

    private static void validateTileCount(long tileCount) throws IOException {
        if (tileCount > CaptureTile.MAX_TILE_COUNT) {
            throw new IOException("Capture geometry too large [" + tileCount + " tiles]");
        }
    }

    private void processUncached(TileCache cache, DataInputStream in, CaptureTile.XYWH xywh, CaptureTile[] dirty, int tidx, int value) throws IOException {
        final byte[] tdata = new byte[-value];
        int toffset = 0;
        int tcount;
        final int tdataLength = tdata.length;
        while ((tcount = in.read(tdata, toffset, tdataLength - toffset)) > 0) {
            toffset += tcount;
        }
        final MemByteBuffer out = new MemByteBuffer();
        rle.runLengthDecode(out, new MemByteBuffer(tdata));
        dirty[tidx] = new CaptureTile(tidx, xywh, out);
        cache.add(dirty[tidx]);
    }
}
