package mpo.dayon.common.squeeze;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

import mpo.dayon.common.buffer.MemByteBuffer;
import mpo.dayon.common.capture.Capture;
import mpo.dayon.common.capture.CaptureTile;
import mpo.dayon.common.log.Log;

public class Compressor {
	/**
	 * NONE.
	 */
	// public static final Compressor NULL_COMPRESSOR = new
	// Compressor(CompressionMethod.NONE, new NullRunLengthEncoder(), new
	// NULL_Zipper());

	/**
	 * ZIP (with regular run-length-encoding).
	 */
	private static final Compressor ZIP_COMPRESSOR = new Compressor(CompressionMethod.ZIP, new RegularRunLengthEncoder(), new ZIP_Zipper());

	/**
	 * BZIP2.
	 */
	private static final Compressor BZIP2_COMPRESSOR = new Compressor(CompressionMethod.BZIP2, new NullRunLengthEncoder(), new BZIP2_Zipper());

	/**
	 * LZMA.
	 */
	private static final Compressor LZMA_COMPRESSOR = new Compressor(CompressionMethod.LZMA, new NullRunLengthEncoder(), new LZMA_Zipper());

	private final CompressionMethod method;

	private final RunLengthEncoder rle;

	private final Zipper zipper;

	private Compressor(CompressionMethod method, RunLengthEncoder rle, Zipper zipper) {
		this.method = method;
		this.rle = rle;
		this.zipper = zipper;
	}

	public static Compressor get(CompressionMethod method) {
		// if (NULL_COMPRESSOR.method == method)
		// {
		// return NULL_COMPRESSOR;
		// }

		if (ZIP_COMPRESSOR.method == method) {
			return ZIP_COMPRESSOR;
		}

		if (BZIP2_COMPRESSOR.method == method) {
			return BZIP2_COMPRESSOR;
		}

		if (LZMA_COMPRESSOR.method == method) {
			return LZMA_COMPRESSOR;
		}

		throw new RuntimeException("Unsupported compressor configuration [" + method + "]!");
	}

	public CompressionMethod getMethod() {
		return method;
	}

	public MemByteBuffer compress(TileCache cache, Capture capture) throws IOException {
		final MemByteBuffer encoded = new MemByteBuffer();

		encoded.writeInt(capture.getId());
		encoded.write(capture.isReset() ? 1 : 0);
		encoded.write(capture.getSkipped()); // as a byte (!)
		encoded.write(capture.getMerged()); // as a byte (!)

		if (capture.isReset()) {
			Log.info("Clear compressor cache [tile:" + capture.getId() + "]");
			cache.clear(); // here for symmetry with the de-compressor (!)
		}

		encoded.writeShort(capture.getWidth());
		encoded.writeShort(capture.getHeight());

		encoded.writeShort(capture.getTWidth());
		encoded.writeShort(capture.getTHeight());

		final CaptureTile[] tiles = capture.getDirtyTiles();

		int idx = 0;

		while (idx < tiles.length) {
			final int markerCount = computeMarkerCount(tiles, idx);

			if (markerCount > 0) {
				encoded.write(markerCount); // non-null tile(s) count

				for (int tidx = idx; tidx < idx + markerCount; tidx++) {
					encodeTile(cache, rle, encoded, tiles[tidx]);
				}

				idx += markerCount;
			} else {
				encoded.write(markerCount); // null tile(s) count
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

		if (tile == null) {
			int count = 0;

			while (count < 128 && from < tiles.length && tiles[from++] == null) {
				++count;
			}

			return -count;
		} else {
			int count = 1;

			while (count < 127 && from < tiles.length && tiles[from++] != null) {
				++count;
			}

			return count;
		}
	}

	private static void encodeTile(TileCache cache, RunLengthEncoder encoder, MemByteBuffer encoded, CaptureTile tile) throws IOException {
		// single-level tile : [ 0 .. 256 [

		if (tile.getSingleLevel() != -1) {
			final int val = tile.getSingleLevel() & 0xFF;

			if (val > 255) {
				throw new RuntimeException("Ouch!");
			}

			encoded.writeShort(val);
			return;
		}

		// multi-level tile : cached [256]

		final int cacheId = cache.getCacheId(tile);

		if (cache.get(cacheId) != CaptureTile.MISSING) // LRU usage (!)
		{
			encoded.writeShort(256);
			encoded.writeInt(cacheId);
			return;
		}

		// multi-level tile (not-cached) [ -32768 .. 0 [

		final int mark = encoded.mark();
		encoded.writeShort(42); // dunno yet (!)
		encoder.runLengthEncode(encoded, tile.getCapture());
		encoded.writeLenAsShort(mark);

		cache.add(tile);
	}

	public Capture decompress(TileCache cache, MemByteBuffer zipped) throws IOException {
		final MemByteBuffer unzipped = zipper.unzip(zipped);

		final DataInputStream in = new DataInputStream(new ByteArrayInputStream(unzipped.getInternal(), 0, unzipped.size()));

		final int cId = in.readInt();
		final boolean cReset = in.read() == 1;

		if (cReset) {
			Log.info("Clear de-compressor cache [tile:" + cId + "]");
			cache.clear();
		}

		final int cSkipped = in.readByte() & 0xFF;
		final int cMerged = in.readByte() & 0xFF;

		final int cWidth = in.readShort();
		final int cHeight = in.readShort();

		final int tWidth = in.readShort();
		final int tHeight = in.readShort();

		final CaptureTile.XYWH[] xywh = CaptureTile.getXYWH(cWidth, cHeight, tWidth, tHeight);

		final CaptureTile[] dirty = new CaptureTile[xywh.length];

		int idx = 0;

		while (idx < dirty.length) {
			final int markerCount = in.readByte();

			if (markerCount > 0) // non-null tile(s)
			{
				for (int tidx = idx; tidx < idx + markerCount; tidx++) {
					final int value = in.readShort();

					if (value >= 0 && value < 256) // single-level
					{
						dirty[tidx] = new CaptureTile(cId, tidx, xywh[tidx], (byte) value);
					} else if (value == 256) // multi-level (cached)
					{
						final int cachedId = in.readInt();
						final CaptureTile cached = cache.get(cachedId); // LRU
																		// usage
																		// (!)

						dirty[tidx] = new CaptureTile(cId, tidx, xywh[tidx], cached);
					} else // multi-level (not cached)
					{
						final int tlen = -value;
						final byte[] tdata = new byte[tlen];

						int toffset = 0;
						int tcount;

						while ((tcount = in.read(tdata, toffset, tdata.length - toffset)) > 0) {
							toffset += tcount;
						}

						final MemByteBuffer out = new MemByteBuffer();
						rle.runLengthDecode(out, new MemByteBuffer(tdata));

						cache.add(dirty[tidx] = new CaptureTile(cId, tidx, xywh[tidx], out));
					}
				}

				idx += markerCount;
			} else // null tile(s)
			{
				idx += (-markerCount + 1);
			}
		}

		return new Capture(cId, cReset, cSkipped, cMerged, cWidth, cHeight, tWidth, tHeight, dirty);
	}
}
