package mpo.dayon.common.squeeze;

import mpo.dayon.common.buffer.MemByteBuffer;

public class NullRunLengthEncoder extends RunLengthEncoder {
	@Override
    public void runLengthEncode(MemByteBuffer out, MemByteBuffer capture) {
		out.write(capture.getInternal(), 0, capture.size());
	}

	@Override
    public void runLengthDecode(MemByteBuffer out, MemByteBuffer encoded) {
		out.write(encoded.getInternal(), 0, encoded.size());
	}
}
