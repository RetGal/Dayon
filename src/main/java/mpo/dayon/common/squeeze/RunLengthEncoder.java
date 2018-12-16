package mpo.dayon.common.squeeze;

import mpo.dayon.common.buffer.MemByteBuffer;

abstract class RunLengthEncoder {
	public abstract void runLengthEncode(MemByteBuffer out, MemByteBuffer capture);

	public abstract void runLengthDecode(MemByteBuffer out, MemByteBuffer encoded);

}
