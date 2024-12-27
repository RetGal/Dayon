package mpo.dayon.common.capture;

import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.util.AbstractMap;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import mpo.dayon.common.buffer.MemByteBuffer;
import mpo.dayon.common.log.Log;

import static java.util.Arrays.stream;

public class Capture {
	private final int id;

	private final boolean reset;

	/**
	 * @see #mergeDirtyTiles(Capture[])
	 */
	private final AtomicInteger skipped;

	/**
	 * @see #mergeDirtyTiles(Capture[])
	 */
	private final AtomicInteger merged;

	private final Dimension captureDimension;

	private final Dimension tileDimension;

	private final CaptureTile[] dirty;

	public Capture(int captureId, boolean reset, int skipped, int merged, Dimension captureDimension, Dimension tileDimension, CaptureTile[] dirty) {
		this.id = captureId;
		this.reset = reset;
		this.skipped = new AtomicInteger(skipped);
		this.merged = new AtomicInteger(merged);
		this.captureDimension = new Dimension(captureDimension);
		this.tileDimension = new Dimension(tileDimension);
		this.dirty = dirty.clone();
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

	private CaptureTile[] getDirty() {
		return dirty;
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
	 * amount as I want to see the impact on the compression of using a smaller
	 * number of gray levels.
	 */
	private int computeInitialByteCount() {
		return stream(dirty).filter(Objects::nonNull).mapToInt(tile -> tile.getCapture().size()).sum();
	}

	public int getWidth() {
		return captureDimension.width;
	}

	public int getHeight() {
		return captureDimension.height;
	}

	public int getTWidth() {
		return tileDimension.width;
	}

	public int getTHeight() {
		return tileDimension.height;
	}

	public int getDirtyTileCount() {
		return (int) stream(dirty).filter(Objects::nonNull).count();
	}

	public CaptureTile[] getDirtyTiles() {
		return dirty.clone();
	}

	public void mergeDirtyTiles(Capture[] olders) {
		int xskipped = 0;
		int xmerged = 0;
		for (Capture older : olders) {
			doMergeDirtyTiles(older);
			xskipped += older.getSkipped();
			xmerged += older.getMerged();
		}
		skipped.addAndGet(xskipped);
		merged.set(1 + xmerged);
		Log.warn(String.format("Merged [id:%d][count:%d][skipped:%d][merged:%d]", id, olders.length, skipped.get(), merged.get()));
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
		if (dirty.length != older.getDirty().length) {
			return; // we're keeping the newest (FULL capture anyway)
		}

		CaptureTile[] olderDirty = older.getDirty();
		for (int idx = 0; idx < dirty.length; idx++) {
			if (olderDirty[idx] != null && dirty[idx] == null) {
				dirty[idx] = olderDirty[idx];
			}
		}
	}

	/**
	 * Tile-rectangle buffer to screen-rectangle buffer.
	 */
	public AbstractMap.SimpleEntry<BufferedImage, byte[]> createBufferedImage(byte[] prevBuffer, int prevWidth, int prevHeight) {
		final boolean isGray = stream(dirty)
				.anyMatch(tile -> tile != null && tile.getCapture().size() == tile.getWidth() * tile.getHeight());
		return isGray ? createBufferedMonochromeImage(prevBuffer, prevWidth, prevHeight) : createBufferedColorImage(prevBuffer, prevWidth, prevHeight);
	}

	/**
	 * Tile-rectangle buffer to screen-rectangle buffer. (monochromatic, 1 byte per pixel)
	 */
    private AbstractMap.SimpleEntry<BufferedImage, byte[]> createBufferedMonochromeImage(byte[] prevBuffer, int prevWidth, int prevHeight) {
		final int capWidth = captureDimension.width;
		final int capHeight = captureDimension.height;
		final byte[] buffer = (prevBuffer != null && capWidth == prevWidth && capHeight == prevHeight && prevBuffer.length == capWidth * capHeight) ? prevBuffer : new byte[capWidth * capHeight];
		stream(dirty)
				.parallel()
				.filter(Objects::nonNull)
				.forEach(tile -> {
					final MemByteBuffer src = tile.getCapture();
					final int tileWidth = tile.getWidth();
					final int srcSize = tileWidth * tile.getHeight();
					int destPos = tile.getY() * capWidth + tile.getX();
					for (int srcPos = 0; srcPos < srcSize; srcPos += tileWidth) {
						System.arraycopy(src.getInternal(), srcPos, buffer, destPos, tileWidth);
						destPos += capWidth;
					}
				});

		final DataBufferByte dbuffer = new DataBufferByte(buffer, buffer.length);
		final WritableRaster raster = Raster.createInterleavedRaster(dbuffer, capWidth, capHeight, capWidth, 1, new int[] { 0 }, null);
		final ColorModel cm = new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_GRAY), new int[] { 8 }, false, false, Transparency.OPAQUE, DataBuffer.TYPE_BYTE);
		return new AbstractMap.SimpleEntry<>(new BufferedImage(cm, raster, false, null), buffer);
	}

	/**
	 * Tile-rectangle buffer to screen-rectangle buffer. (color, 4 bytes per pixel)
	 */
    private AbstractMap.SimpleEntry<BufferedImage, byte[]> createBufferedColorImage(byte[] prevBuffer, int prevWidth, int prevHeight) {
		final int capWidth = captureDimension.width;
		final int capHeight = captureDimension.height;
		final byte[] buffer = (prevBuffer != null && capWidth == prevWidth && capHeight == prevHeight && prevBuffer.length == capWidth * capHeight * 4) ? prevBuffer : new byte[capWidth * capHeight * 4];
		final int capWidthByteSize = capWidth * 4;
		stream(dirty)
				.parallel()
				.filter(Objects::nonNull)
				.forEach(tile -> {
					final MemByteBuffer src = tile.getCapture();
					final int tileWidthByteSize = tile.getWidth() * 4;
					final int srcSize = tileWidthByteSize * tile.getHeight();
					int destPos = tile.getY() * capWidthByteSize + tile.getX() * 4;
					for (int srcPos = 0; srcPos < srcSize; srcPos += tileWidthByteSize) {
						System.arraycopy(src.getInternal(), srcPos, buffer, destPos, tileWidthByteSize);
						destPos += capWidthByteSize;
					}
				});

		final DataBufferByte dbuffer = new DataBufferByte(buffer, buffer.length);
		final WritableRaster raster = Raster.createInterleavedRaster(dbuffer, capWidth, capHeight, capWidthByteSize , 4, new int[] { 1, 2, 3 }, null);
		final ColorModel cm = new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB), false, false, Transparency.OPAQUE, DataBuffer.TYPE_BYTE);
		return new AbstractMap.SimpleEntry<>(new BufferedImage(cm, raster, false, null), buffer);
	}
}
