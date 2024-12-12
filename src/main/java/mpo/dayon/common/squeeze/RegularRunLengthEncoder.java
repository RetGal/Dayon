package mpo.dayon.common.squeeze;

import mpo.dayon.common.buffer.MemByteBuffer;

public class RegularRunLengthEncoder implements RunLengthEncoder {
    @Override
    public void runLengthEncode(MemByteBuffer out, MemByteBuffer capture) {
        final byte[] xCapture = capture.getInternal();
        final int len = capture.size();
        int pos = 0;
        int prev = Integer.MIN_VALUE;
        while (pos < len) {
            final int current = xCapture[pos];
            out.write(current);
            if (current != prev) {
                prev = current;
                ++pos;
            } else // We've got 2 same symbols ...
            {
                int count = 0;
                int noMatch = 0;
                while (count < 255 && ++pos < len && (noMatch = xCapture[pos]) == current) {
                    ++count;
                }
                if (count == 255) {
                    out.write(255);
                    prev = Integer.MIN_VALUE;
                    ++pos;
                } else if (pos < len) {
                    out.write(count);
                    prev = noMatch;
                    out.write(prev);
                    ++pos;
                } else {
                    writeNonZero(out, count);
                    break;
                }
            }
        }
    }

    private static void writeNonZero(MemByteBuffer out, int count) {
        if (count > 0) {
            out.write(count);
        }
    }

    @Override
    public void runLengthDecode(MemByteBuffer out, MemByteBuffer encoded) {
        final byte[] xEncoded = encoded.getInternal();
        final int len = encoded.size();
        int pos = 0;
        int prev = Integer.MIN_VALUE;
        while (pos < len) {
            final int current = xEncoded[pos++];
            out.write(current);
            if (current != prev) {
                prev = current;
            } else if (pos < len) {
                final int count = xEncoded[pos++] & 0xFF;
                out.fill(count, current);
                prev = Integer.MIN_VALUE;
            }
        }
    }
}