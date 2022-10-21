package mpo.dayon.common.squeeze;

import mpo.dayon.common.buffer.MemByteBuffer;

public class PackBitsRunLengthEncoder implements RunLengthEncoder {
	@Override
    public void runLengthEncode(MemByteBuffer out, MemByteBuffer capture) {
		final byte[] xcapture = capture.getInternal();
		final int len = capture.size();
		int pprev = -1;
		int prev = -1;
		int start = 0;
		int pos = 0;
		while (pos < len) {
			final int current = xcapture[pos];
			if (current == prev && prev == pprev) {
				if (start < pos - 2) {
					encodeVerbatim(out, xcapture, start, pos - 2);
				}
				start = pos = encodeRun(out, xcapture, pos - 2);
				pprev = prev = -1;
			} else {
				pprev = prev;
				prev = current;
				++pos;
			}
		}
		if (len > 0) {
			encodeVerbatim(out, xcapture, start, len);
		}
	}

    private void encodeVerbatim(MemByteBuffer out, byte[] in, int start, int end) {
		// [ start .. end [
        int verbatimMax = 128;
        final int d = (end - start) / verbatimMax;
		for (int idx = 0; idx < d; idx++) {
			out.write(verbatimMax - 1);
			out.arraycopy(in, start + idx * verbatimMax, verbatimMax);
		}
		final int m = (end - start) % verbatimMax;
		if (m > 0) {
			out.write(m - 1);
			out.arraycopy(in, start + d * verbatimMax, m);
		}
	}

    private int encodeRun(MemByteBuffer out, byte[] in, int from) {
		final int val = in[from];
		int pos = from;
		while (pos < in.length && in[pos] == val) {
			++pos;
		}
		// [ from .. pos [
        int runMax = 130;
        final int d = (pos - from) / runMax;
		for (int idx = 0; idx < d; idx++) {
			out.write(2 - runMax);
			out.write(val);
		}
		final int m = (pos - from) % runMax;
		if (m > 2) {
			out.write(2 - m);
			out.write(val);
		} else if (m > 0) // we've 2 elements that cannot be included in that run
		{
			pos -= m;
		}
		return pos;
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