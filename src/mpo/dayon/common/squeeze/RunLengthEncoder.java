package mpo.dayon.common.squeeze;

import java.io.IOException;

import mpo.dayon.common.buffer.MemByteBuffer;

abstract class RunLengthEncoder {
	public abstract void runLengthEncode(MemByteBuffer out, MemByteBuffer capture) throws IOException;

	public abstract void runLengthDecode(MemByteBuffer out, MemByteBuffer encoded) throws IOException;

}
