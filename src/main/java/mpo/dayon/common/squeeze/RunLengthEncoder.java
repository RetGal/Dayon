package mpo.dayon.common.squeeze;

import mpo.dayon.common.buffer.MemByteBuffer;

interface RunLengthEncoder {
	void runLengthEncode(MemByteBuffer out, MemByteBuffer capture);

	void runLengthDecode(MemByteBuffer out, MemByteBuffer encoded);

}
