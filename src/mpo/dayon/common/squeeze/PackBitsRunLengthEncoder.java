package mpo.dayon.common.squeeze;

import java.io.IOException;

import mpo.dayon.common.buffer.MemByteBuffer;

public class PackBitsRunLengthEncoder extends RunLengthEncoder {
	public void runLengthEncode(MemByteBuffer out, MemByteBuffer capture) throws IOException {
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

	private final int VERBATIM_MAX = 128;

	private void encodeVerbatim(MemByteBuffer out, byte[] in, int start, int end) throws IOException {
		// [ start .. end [

		final int d = (end - start) / VERBATIM_MAX;

		for (int idx = 0; idx < d; idx++) {
			out.write(VERBATIM_MAX - 1);
			out.arraycopy(in, start + idx * VERBATIM_MAX, VERBATIM_MAX);
		}

		final int m = (end - start) % VERBATIM_MAX;

		if (m > 0) {
			out.write(m - 1);
			out.arraycopy(in, start + d * VERBATIM_MAX, m);
		}
	}

	private final int RUN_MAX = 130;

	private int encodeRun(MemByteBuffer out, byte[] in, int from) throws IOException {
		final int val = in[from];

		int pos = from;

		while (pos < in.length && in[pos] == val) {
			++pos;
		}

		// [ from .. pos [

		final int d = (pos - from) / RUN_MAX;

		for (int idx = 0; idx < d; idx++) {
			out.write(2 - RUN_MAX);
			out.write(val);
		}

		final int m = (pos - from) % RUN_MAX;

		if (m > 2) {
			out.write(2 - m);
			out.write(val);
		} else if (m > 0) // we've 2 elements that cannot be included in that
							// run
		{
			pos -= m;
		}

		return pos;
	}

	public void runLengthDecode(MemByteBuffer out, MemByteBuffer encoded) throws IOException {
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