package mpo.dayon.common.squeeze;

import java.io.IOException;

import mpo.dayon.common.buffer.MemByteBuffer;

interface Zipper {
	MemByteBuffer zip(MemByteBuffer unzipped) throws IOException;

	MemByteBuffer unzip(MemByteBuffer zipped) throws IOException;

}
