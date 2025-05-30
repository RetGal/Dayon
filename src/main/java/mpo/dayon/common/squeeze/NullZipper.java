package mpo.dayon.common.squeeze;

import mpo.dayon.common.buffer.MemByteBuffer;

public class NullZipper implements Zipper {
	@Override
    public MemByteBuffer zip(MemByteBuffer unzipped) {
		final MemByteBuffer zipped = MemByteBuffer.acquire(unzipped.size());
		zipped.write(unzipped.getInternal(), 0, unzipped.size());
		unzipped.release();
		return zipped;
	}

	@Override
    public MemByteBuffer unzip(MemByteBuffer zipped) {
		final MemByteBuffer unzipped = MemByteBuffer.acquire(zipped.size());
		unzipped.write(zipped.getInternal(), 0, zipped.size());
		zipped.release();
		return unzipped;
	}
}
