package mpo.dayon.common.capture;

import java.util.Arrays;
import java.util.zip.Adler32;
import java.util.zip.Checksum;

import mpo.dayon.common.buffer.MemByteBuffer;
import mpo.dayon.common.gui.common.Position;

public class CaptureTile {
	public static final CaptureTile MISSING = new CaptureTile();

	private final long checksum;

	private final Position position;

	private final int width;

	private final int height;

	private final MemByteBuffer capture;

	private final byte singleLevel;

	/**
	 * Created from a cache - testing purpose - I've to identify that kind of
	 * tile as the Adler32 is not perfect and from time to time I've a few
	 * erroneous pixels when comparing initial capture (coming from the
	 * assisted) to the decompressed captures in the assistant.
	 */
	private final boolean fromCache;

	private CaptureTile() {
		this.checksum = -1;
		this.position = new Position(-1, -1);
		this.width = -1;
		this.height = -1;
		this.capture = null;
		this.singleLevel = -1;
		this.fromCache = false;
	}

	public CaptureTile(long checksum, Position position, int width, int height, byte[] capture) {
		this.checksum = checksum;
		this.position = position;
		this.width = width;
		this.height = height;
		this.capture = new MemByteBuffer(capture);
		this.singleLevel = computeSingleLevel(capture);
		this.fromCache = false;
	}

	/**
	 * Assisted to assistant : result of network data decompression.
	 */
	public CaptureTile(XYWH xywh, MemByteBuffer capture) {
		this.checksum = computeChecksum(capture.getInternal(), 0, capture.size()); // cache usage (!)
		this.position = new Position(xywh.x, xywh.y);
		this.width = xywh.w;
		this.height = xywh.h;
		this.capture = capture;
		this.singleLevel = -1;
		this.fromCache = false;
	}

	/**
	 * Assisted to assistant : result of network data decompression (single level tile).
	 */
	public CaptureTile(XYWH xywh, byte singleLevel) {
		this.checksum = -1;
		this.position = new Position(xywh.x, xywh.y);
		this.width = xywh.w;
		this.height = xywh.h;
		final byte[] data = new byte[width * height * 4];
		Arrays.fill(data, singleLevel);
		this.capture = new MemByteBuffer(data);
		this.singleLevel = singleLevel;
		this.fromCache = false;
	}

	/**
	 * Assisted to assistant : result of network data decompression (from the cache).
	 */
	public CaptureTile(XYWH xywh, CaptureTile cached) {
		this.checksum = -1;
		this.position = new Position(xywh.x, xywh.y);
		this.width = xywh.w;
		this.height = xywh.h;
		this.capture = (cached == MISSING) ? new MemByteBuffer(new byte[width * height * 4]) // black image (!)
				: cached.getCapture(); // sharing it (!)
		this.singleLevel = -1;
		this.fromCache = true;
	}

	public static long computeChecksum(byte[] data, int offset, int len) {
		final Checksum checksum = new Adler32();
		// final Checksum checksum = new CRC32(); -- more CPU - Adler32 seems
		// quite good until now ...
		checksum.update(data, offset, len);
		return checksum.getValue();
	}

	public long getChecksum() {
		return checksum;
	}

	public int getX() {
		return position.getX();
	}

	public int getY() {
		return position.getY();
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	public MemByteBuffer getCapture() {
		return capture;
	}

	/**
	 * @return -1 if not applicable.
	 */
	public int getSingleLevel() {
		return singleLevel;
	}

	public boolean isFromCache() {
		return fromCache;
	}

	private static byte computeSingleLevel(byte[] capture) {
		final byte level = capture[0];
		for (int idx = 1; idx < capture.length; idx++) {
			if (capture[idx] != level) {
				return -1; // multi-level
			}
		}
		return level;
	}

	// =================================================================================================================
	//
	// A bit of caching to compute the conversion : t-id => tx, ty, tw, th
	//
	// =================================================================================================================

	public static class XYWH {
		final int x;
		final int y;
		final int w;
		final int h;

		XYWH(int x, int y, int w, int h) {
			this.x = x;
			this.y = y;
			this.w = w;
			this.h = h;
		}

		public boolean equals(int x, int y, int w, int h) {
			return x == this.x && y == this.y && w == this.w && h == this.h;
		}
	}

	private static class XYWH_Configuration {
		final int captureWidth;
		final int captureHeight;
		final int tileWidth;
		final int tileHeight;

		XYWH_Configuration(int captureWidth, int captureHeight, int tileWidth, int tileHeight) {
			this.captureWidth = captureWidth;
			this.captureHeight = captureHeight;
			this.tileWidth = tileWidth;
			this.tileHeight = tileHeight;
		}

		boolean equals(int captureWidth, int captureHeight, int tileWidth, int tileHeight) {
			return captureHeight == this.captureHeight && captureWidth == this.captureWidth && tileWidth == this.tileWidth && tileHeight == this.tileHeight;
		}
	}

	private static class XYWH_Cache {
		final XYWH_Configuration configuration;

		final XYWH[] xywh;

		XYWH_Cache(XYWH_Configuration configuration, XYWH[] xywh) {
			this.configuration = configuration;
			this.xywh = xywh;
		}
	}

	private static XYWH_Cache cachedXYWH;

	/**
	 * The cache might change in case the assistant is re-configuring the
	 * assisted capture configuration (capture width/height and/or tile
	 * width/height); currently not possible.
	 * <p/>
	 * Remember that the last tile (either along the X axis or the Z axis might
	 * not be tile width/height) due to rounding error; e.g., 1920 / 32 = 60 --
	 * 1200 / 32 = 37.5
	 */
	public static XYWH[] getXYWH(int captureWidth, int captureHeight, int tileWidth, int tileHeight) {
		synchronized (CaptureTile.class) {
			if (cachedXYWH == null || !cachedXYWH.configuration.equals(captureWidth, captureHeight, tileWidth, tileHeight)) {
				cachedXYWH = computeXYWH(captureWidth, captureHeight, tileWidth, tileHeight);
			}
			return cachedXYWH.xywh.clone();
		}
	}

	private static XYWH_Cache computeXYWH(int captureWidth, int captureHeight, int tileWidth, int tileHeight) {
		final int x = (captureWidth + tileWidth - 1) / tileWidth;
		final int y = (captureHeight + tileHeight - 1) / tileHeight;
		final XYWH[] xywh = new XYWH[x * y];
		int tileId = 0;
		for (int ty = 0; ty < captureHeight; ty += tileHeight) {
			final int th = Math.min(captureHeight - ty, tileHeight);
			for (int tx = 0; tx < captureWidth; tx += tileWidth) {
				final int tw = Math.min(captureWidth - tx, tileWidth);
				xywh[tileId++] = new XYWH(tx, ty, tw, th);
			}
		}
        return new XYWH_Cache(new XYWH_Configuration(captureWidth, captureHeight, tileWidth, tileHeight), xywh);
	}
}
