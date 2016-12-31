package mpo.dayon.common.squeeze;

import java.io.IOException;

import mpo.dayon.common.buffer.MemByteBuffer;

abstract class Zipper {
	public abstract MemByteBuffer zip(MemByteBuffer unzipped) throws IOException;

	public abstract MemByteBuffer unzip(MemByteBuffer zipped) throws IOException;

}
