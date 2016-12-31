package mpo.dayon.common.squeeze;

import java.io.IOException;

import mpo.dayon.common.buffer.MemByteBuffer;

public class RegularRunLengthEncoder extends RunLengthEncoder {
	public void runLengthEncode(MemByteBuffer out, MemByteBuffer capture) throws IOException {
		final byte[] xcapture = capture.getInternal();
		final int len = capture.size();

		int pos = 0;
		int prev = Integer.MIN_VALUE;

		while (pos < len) {
			final int current = xcapture[pos];
			out.write(current);

			if (current != prev) {
				prev = current;
				++pos;
			} else // We've got 2 same symbols ...
			{
				int count = 0;
				int nomatch = 0;

				while (count < 255 && ++pos < len && (nomatch = xcapture[pos]) == current) {
					++count;
				}

				if (count == 255) {
					out.write(count);
					prev = Integer.MIN_VALUE;
					++pos;
				} else if (pos < len) {
					out.write(count);
					out.write(prev = nomatch);
					++pos;
				} else {
					if (count > 0) {
						out.write(count);
					}
					break;
				}
			}
		}
	}

	public void runLengthDecode(MemByteBuffer out, MemByteBuffer encoded) throws IOException {
		final byte[] xencoded = encoded.getInternal();
		final int len = encoded.size();

		int pos = 0;
		int prev = Integer.MIN_VALUE;

		while (pos < len) {
			final int current = xencoded[pos++];
			out.write(current);

			if (current != prev) {
				prev = current;
			} else if (pos < len) {
				final int count = xencoded[pos++] & 0xFF;
				out.fill(count, current);

				prev = Integer.MIN_VALUE;
			}
		}
	}

}