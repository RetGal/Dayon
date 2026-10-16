package mpo.dayon.common.squeeze;

import mpo.dayon.common.buffer.MemByteBuffer;

public class NullZipper implements Zipper {
	@Override
    public MemByteBuffer zip(MemByteBuffer unzipped) {
		final MemByteBuffer zipped = new MemByteBuffer();
		unzipped.appendTo(zipped);
		return zipped;
	}

	@Override
    public MemByteBuffer unzip(MemByteBuffer zipped) {
		final MemByteBuffer unzipped = new MemByteBuffer();
		zipped.appendTo(unzipped);
		return unzipped;
	}
}
