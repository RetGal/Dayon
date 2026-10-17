package mpo.dayon.common.squeeze;

import mpo.dayon.common.buffer.MemByteBuffer;

public class PackBitsRunLengthEncoder implements RunLengthEncoder {
	@Override
    public void runLengthEncode(MemByteBuffer out, MemByteBuffer capture) {
		final int len = capture.size();
		final byte[] snapshot = out == capture ? capture.getInternal() : null;
		int pprev = -1;
		int prev = -1;
		int start = 0;
		int pos = 0;
		while (pos < len) {
			final int current = byteAt(capture, snapshot, pos);
			if (current == prev && prev == pprev) {
				if (start < pos - 2) {
					encodeVerbatim(out, capture, snapshot, start, pos - 2);
				}
				start = pos = encodeRun(out, capture, snapshot, pos - 2, len);
				pprev = prev = -1;
			} else {
				pprev = prev;
				prev = current;
				++pos;
			}
		}
		if (len > 0) {
			encodeVerbatim(out, capture, snapshot, start, len);
		}
	}

    private void encodeVerbatim(MemByteBuffer out, MemByteBuffer in, byte[] snapshot, int start, int end) {
		// [ start .. end [
        int verbatimMax = 128;
        final int d = (end - start) / verbatimMax;
		for (int idx = 0; idx < d; idx++) {
			out.write(verbatimMax - 1);
			appendRange(out, in, snapshot, start + idx * verbatimMax, verbatimMax);
		}
		final int m = (end - start) % verbatimMax;
		if (m > 0) {
			out.write(m - 1);
			appendRange(out, in, snapshot, start + d * verbatimMax, m);
		}
	}

    private int encodeRun(MemByteBuffer out, MemByteBuffer in, byte[] snapshot, int from, int len) {
		final int val = byteAt(in, snapshot, from);
		int pos = from;
		while (pos < len && byteAt(in, snapshot, pos) == val) {
			++pos;
		}
		// [ from .. pos [
        int runMax = 130;
        final int d = (pos - from) / runMax;
		for (int idx = 0; idx < d; idx++) {
			out.writes(2 - runMax, val);
		}
		final int m = (pos - from) % runMax;
		if (m > 2) {
			out.writes(2 - m, val);
		} else if (m > 0) // we've 2 elements that cannot be included in that run
		{
			pos -= m;
		}
		return pos;
	}

	private static int byteAt(MemByteBuffer in, byte[] snapshot, int index) {
		return snapshot == null ? in.getByte(index) : snapshot[index];
	}

	private static void appendRange(MemByteBuffer out, MemByteBuffer in, byte[] snapshot, int start, int len) {
		if (snapshot == null) {
			in.appendRangeTo(out, start, len);
		} else {
			out.write(snapshot, start, len);
		}
	}

	@Override
    public void runLengthDecode(MemByteBuffer out, MemByteBuffer encoded) {
		final byte[] xencoded = encoded.getInternal();
		final int len = encoded.size();
		int pos = 0;
		while (pos < len) {
			final int count = xencoded[pos];
			if (count < 0) {
				out.fill(2 - count, xencoded[++pos]);
			} else {
				final int rlen = count + 1;
				for (int idx = 0; idx < rlen; idx++) {
					out.write(xencoded[++pos]);
				}
			}
			++pos;
		}
	}
}