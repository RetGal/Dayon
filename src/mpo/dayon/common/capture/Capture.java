package mpo.dayon.common.capture;

import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.util.concurrent.atomic.AtomicInteger;

import org.jetbrains.annotations.Nullable;

import mpo.dayon.common.buffer.MemByteBuffer;
import mpo.dayon.common.log.Log;
import mpo.dayon.common.utils.Pair;

public class Capture {
	private final int id;

	private final boolean reset;

	/**
	 * @see #mergeDirtyTiles(Capture[])
	 */
	private AtomicInteger skipped;

	/**
	 * @see #mergeDirtyTiles(Capture[])
	 */
	private AtomicInteger merged;

	private final int width;

	private final int height;

	private final int tWidth;

	private final int tHeight;

	private final CaptureTile[] dirty;

	public Capture(int captureId, boolean reset, int skipped, int merged, int width, int height, int tWidth, int tHeight, CaptureTile[] dirty) {
		this.id = captureId;
		this.reset = reset;

		this.skipped = new AtomicInteger(skipped);
		this.merged = new AtomicInteger(merged);

		this.width = width;
		this.height = height;

		this.tWidth = tWidth;
		this.tHeight = tHeight;

		this.dirty = dirty;
	}

	public int getId() {
		return id;
	}

	public boolean isReset() {
		return reset;
	}

	public int getSkipped() {
		return skipped.get();
	}

	public int getMerged() {
		return merged.get();
	}

	/**
	 * @see #computeInitialByteCount()
	 */
	public double computeCompressionRatio(int compressed) {
		return computeInitialByteCount() / (double) compressed;
	}

	/**
	 * Based on the gray level data (not the RGB capture).
	 * <p/>
	 * That's the actual payload size of a capture to paint it on the screen -
	 * that's the only amount of data I would need to send to the assistant over
	 * the network. But I've to send some extra-info for the location, size,
	 * etc... as well as some un-marshalling extra-stuff (e.g., len). Then, I'm
	 * going to encode and compress all that data and I want to compute on the
	 * assistant side the actual compression ratio compared to that initial
	 * amount of byte.
	 * <p/>
	 * Note that the actual number of gray levels does not change that original
	 * amount as I want to see the impact on the compression of using less
	 * number of gray levels.
	 */
	private int computeInitialByteCount() {
		int count = 0;

		for (final CaptureTile tile : dirty) {
			if (tile != null) {
				count += tile.getCapture().size();
			}
		}

		return count;
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	public int getTWidth() {
		return tWidth;
	}

	public int getTHeight() {
		return tHeight;
	}

	public int getDirtyTileCount() {
		int count = 0;

		for (final CaptureTile tile : dirty) {
			if (tile != null) {
				++count;
			}
		}

		return count;
	}

	public CaptureTile[] getDirtyTiles() {
		return dirty;
	}

	public void mergeDirtyTiles(Capture[] olders) {
		int xskipped = 0;
		int xmerged = 0;

		for (final Capture older : olders) {
			doMergeDirtyTiles(older);

			xskipped += older.skipped.get();
			xmerged += older.merged.get();
		}

		skipped.addAndGet(xskipped);
		merged.set(1 + xmerged);

		Log.warn(String.format("Merged [id:%d] [count:%d] [skipped:%d][merged:%d]", id, olders.length, skipped, merged));
	}

	/**
	 * <pre>
	 * [ this ] [+] [ older ]
	 *    x            -       :  this.tile  =  this.tile
	 *    x            x       :  this.tile  =  this.tile
	 *    -            x       :  this.tile  =  older.tile
	 * </pre>
	 */
	private void doMergeDirtyTiles(Capture older) {
		// The only way the tile 'length' may change is when the capture engine
		// has been re-configured.
		// In that case (for the sake of simplicity) a FULL capture will be
		// sent.

		if (dirty.length != older.dirty.length) {
			return; // we're keeping the newest (FULL capture anyway)
		}

		for (int idx = 0; idx < dirty.length; idx++) {
			final CaptureTile thisTile = dirty[idx];
			final CaptureTile olderTile = older.dirty[idx];

			if (olderTile != null && thisTile == null) {
				dirty[idx] = olderTile;
			}
		}
	}

	/**
	 * Tile-rectangle buffer to screen-rectangle buffer.
	 */
	public Pair<BufferedImage, byte[]> createBufferedImage(@Nullable byte[] prevBuffer, int prevWidth, int prevHeight) {
		final byte[] buffer = new byte[width * height];

		if (prevBuffer != null && width == prevWidth && height == prevHeight) {
			System.arraycopy(prevBuffer, 0, buffer, 0, buffer.length);
		}

		for (final CaptureTile tile : dirty) {
			if (tile != null) {
				final MemByteBuffer src = tile.getCapture();
				final int srcSize = src.size();

				final int tw = tile.getWidth();

				int srcPos = 0;
				int destPos = tile.getY() * width + tile.getX();

				while (srcPos < srcSize) {
					System.arraycopy(src.getInternal(), srcPos, buffer, destPos, tw);

					srcPos += tw;
					destPos += width;
				}
			}
		}

		final DataBuffer dbuffer = new DataBufferByte(buffer, buffer.length);

		final WritableRaster raster = Raster.createInterleavedRaster(dbuffer, width, height, width, // scanlineStride
				1, // pixelStride
				new int[] { 0 }, // bandOffsets
				null);

		final ColorModel cm = new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_GRAY), new int[] { 8 }, false, false, Transparency.OPAQUE,
				DataBuffer.TYPE_BYTE);

		return new Pair<>(new BufferedImage(cm, raster, false, null), buffer);
	}
}
