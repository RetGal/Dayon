package mpo.dayon.common.buffer;

import java.io.OutputStream;
import java.util.Arrays;

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
		return Arrays.copyOf(buffer, count);
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
		ensureCapacity(count + 1);
		buffer[count++] = (byte) val;
	}

	@Override
	public void write(byte[] buffer) {
		write(buffer, 0, buffer.length);
	}

	@Override
	public void write(byte[] buffer, int off, int len) {
		if (len == 0) {
			return;
		}
		ensureCapacity(count + len);
		System.arraycopy(buffer, off, this.buffer, count, len);
		count += len;
	}

	/**
	 * Equivalent to the DataOutputStream version (!)
	 */
	public final void writeInt(int val) {
		ensureCapacity(count + 4);
		buffer[count++] = (byte) ((val >>> 24) & 0xFF);
		buffer[count++] = (byte) ((val >>> 16) & 0xFF);
		buffer[count++] = (byte) ((val >>> 8) & 0xFF);
		buffer[count++] = (byte) (val & 0xFF);
	}

	/**
	 * Equivalent to the DataOutputStream version (!)
	 */
	public final void writeShort(int val) {
		ensureCapacity(count + 2);
		buffer[count++] = (byte) ((val >>> 8) & 0xFF);
		buffer[count++] = (byte) (val & 0xFF);
	}

	public void writeLenAsShort(int mark) {
		final int end = mark();
		final int len = end - mark - 2; // -2: the len (as short) itself (!)
		resetToMark(mark);
		writeShort(-len);
		resetToMark(end);
	}

	public void fill(int len, int val) {
		ensureCapacity(count + len);
		Arrays.fill(buffer, count, count + len, (byte) val);
		count += len;
	}

	public void arraycopy(byte[] in, int start, int len) {
		ensureCapacity(count + len);
		System.arraycopy(in, start, buffer, count, len);
		count += len;
	}

	private void ensureCapacity(int newCount) {
		if (newCount > buffer.length) {
			int newCapacity = Math.max(buffer.length << 1, newCount);
			if (newCapacity < buffer.length * 3 / 2) {
				newCapacity = buffer.length * 3 / 2;
			}
			buffer = Arrays.copyOf(buffer, newCapacity);
		}
	}
}