package mpo.dayon.common.buffer;

import java.io.OutputStream;
import java.util.Arrays;

import org.jetbrains.annotations.NotNull;

/**
 * A mixed between a byte buffer and a byte stream ...
 */
public class MemByteBuffer extends OutputStream {
	private static final int DEFAULT_INITIAL_CAPACITY = 32;

	private byte[] buffer;

	private int count;

	public MemByteBuffer() {
		this(DEFAULT_INITIAL_CAPACITY);
	}

	private MemByteBuffer(int capacity) {
		buffer = new byte[capacity];
	}

	/**
	 * @param data
	 *            the newly created buffer is adopting that byte array (!)
	 */
	public MemByteBuffer(byte[] data) {
		buffer = data;
		count = data.length;
	}

	public int size() {
		return count;
	}

	public byte[] getInternal() {
		return buffer;
	}

	public int mark() {
		return count;
	}

	private void resetToMark(int mark) {
		count = mark;
	}

	/**
	 * Writes the specified byte to this output stream. The general contract for
	 * <code>write</code> is that one byte is written to the output stream. The
	 * byte to be written is the eight low-order bits of the argument
	 * <code>b</code>. The 24 high-order bits of <code>b</code> are ignored.
	 */
	@Override
    public void write(int val) {
		final int newcount = count + 1;

		if (newcount > buffer.length) {
			buffer = Arrays.copyOf(buffer, Math.max(buffer.length << 1, newcount));
		}

		buffer[count++] = (byte) val;
	}

	/**
	 * @see #write(int)
	 */
	private void write(int val1, int val2) {
		final int newcount = count + 2;

		if (newcount > buffer.length) {
			buffer = Arrays.copyOf(buffer, Math.max(buffer.length << 1, newcount));
		}

		buffer[count++] = (byte) val1;
		buffer[count++] = (byte) val2;
	}

	@Override
    public void write(@NotNull byte[] buffer) {
		write(buffer, 0, buffer.length);
	}

	@Override
    public void write(@NotNull byte[] buffer, int off, int len) {
		if (len == 0) {
			return;
		}

		final int newcount = count + len;

		if (newcount > this.buffer.length) {
			this.buffer = Arrays.copyOf(this.buffer, Math.max(this.buffer.length << 1, newcount));
		}

		System.arraycopy(buffer, off, this.buffer, count, len);

		count = newcount;
	}

	/**
	 * Equivalent to the DataOutputStream version (!)
	 */
	public final void writeInt(int val) {
		write((val >>> 24) & 0xFF, (val >>> 16) & 0xFF);
		write((val >>> 8) & 0xFF, val & 0xFF);
	}

	/**
	 * Equivalent to the DataOutputStream version (!)
	 */
	public final void writeShort(int val) {
		write((val >>> 8) & 0xFF, val & 0xFF);
	}

	public void writeLenAsShort(int mark) {
		final int end = mark();
		final int len = end - mark - 2; // -2: the len (as short) itself (!)

		resetToMark(mark);
		writeShort(-len);

		resetToMark(end);
	}

	public void fill(int len, int val) {
		final int newcount = count + len;

		if (newcount > buffer.length) {
			buffer = Arrays.copyOf(buffer, Math.max(buffer.length << 1, newcount));
		}

		for (int idx = count; idx < newcount; idx++) {
			buffer[idx] = (byte) val;
		}

		count = newcount;
	}

	public void arraycopy(byte[] in, int start, int len) {
		final int newcount = count + len;

		if (newcount > buffer.length) {
			buffer = Arrays.copyOf(buffer, Math.max(buffer.length << 1, newcount));
		}

		System.arraycopy(in, start, buffer, count, len);

		count = newcount;
	}
}
