package mpo.dayon.common.buffer;

import mpo.dayon.common.log.Log;

import java.io.OutputStream;
import java.util.Arrays;

import static java.lang.Math.max;

/**
 * A mixed between a byte buffer and a byte stream ...
 */
public class MemByteBuffer extends OutputStream {
	private static final int DEFAULT_INITIAL_CAPACITY = 32;
	private static final int MAX_POOL_SIZE = 16;
	private static final ThreadLocal<MemByteBuffer[]> threadLocalPool = ThreadLocal.withInitial(() -> new MemByteBuffer[MAX_POOL_SIZE]);

	private byte[] buffer;
	private int count;

	private MemByteBuffer(int capacity) {
		buffer = new byte[capacity];
	}

	public static MemByteBuffer[] createCustomPool(int capacity, int size) {
		MemByteBuffer[] buffers = new MemByteBuffer[size];
		for (int i = 0; i < size; i++) {
			buffers[i] = new MemByteBuffer(capacity);
		}
		return buffers;
	}

	public static MemByteBuffer acquire() {
		MemByteBuffer[] pool = threadLocalPool.get();
		for (int i = 0; i < pool.length; i++) {
			if (pool[i] != null) {
				Log.info("Reusing MemByteBuffer from pool at index: " + i);
				MemByteBuffer buf = pool[i];
				pool[i] = null;
				buf.reset();
				return buf;
			}
		}
		Log.info("Creating new MemByteBuffer with default capacity.");
		return new MemByteBuffer(DEFAULT_INITIAL_CAPACITY);
	}

	/**
	 * @param data
	 *            the newly acquired buffer is adopting that byte array (!)
	 */
	public static MemByteBuffer acquire(byte[] data) {
		MemByteBuffer buf = acquire(data.length);
		buf.write(data, 0, data.length);
		return buf;
	}

	public static MemByteBuffer acquire(int capacity) {
		MemByteBuffer[] pool = threadLocalPool.get();
		for (int i = 0; i < pool.length; i++) {
			if (pool[i] != null && pool[i].buffer.length >= capacity) {
				Log.info("Reusing MemByteBuffer from pool at index: " + i);
				MemByteBuffer buf = pool[i];
				pool[i] = null;
				buf.reset();
				return buf;
			}
		}
		Log.info("Creating new MemByteBuffer with " + max(capacity, DEFAULT_INITIAL_CAPACITY) + " bytes capacity.");
		return new MemByteBuffer(max(capacity, DEFAULT_INITIAL_CAPACITY));
	}

	public void release() {
		MemByteBuffer[] pool = threadLocalPool.get();
		for (int i = 0; i < pool.length; i++) {
			if (pool[i] == null) {
				pool[i] = this;
				Log.info("Released MemByteBuffer to pool at index: " + i + " with capacity: " + buffer.length);
				return;
			}
		}
	}

	public void reset() {
		count = 0;
	}

	public int size() {
		return count;
	}

	public int capacity() {
		return buffer.length;
	}

	public byte[] getFullBuffer() {
		return Arrays.copyOf(buffer, buffer.length);
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
			int newCapacity = max(buffer.length << 1, newCount);
			if (newCapacity < buffer.length * 3 / 2) {
				newCapacity = buffer.length * 3 / 2;
			}
			buffer = Arrays.copyOf(buffer, newCapacity);
		}
	}
}