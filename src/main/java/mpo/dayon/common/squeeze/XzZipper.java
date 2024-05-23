package mpo.dayon.common.squeeze;

import mpo.dayon.common.buffer.MemByteBuffer;
import org.tukaani.xz.*;

import java.io.*;

public class XzZipper implements Zipper {

	private final FilterOptions options;

    XzZipper() {
        try {
            options = new LZMA2Options(3);
        } catch (UnsupportedOptionsException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public MemByteBuffer zip(MemByteBuffer unCompressed) throws IOException {
		try (MemByteBuffer compressed = new MemByteBuffer()) {
			try (XZOutputStream xzOutputStream = new XZOutputStream(compressed, options)) {
				xzOutputStream.write(unCompressed.getInternal(), 0, unCompressed.size());
				xzOutputStream.flush();
			}
			return compressed;
		}
	}

	@Override
    public MemByteBuffer unzip(MemByteBuffer compressed) throws IOException {
		try (MemByteBuffer unCompressed = new MemByteBuffer()) {
			try (XZInputStream xzInputStream = new XZInputStream(new ByteArrayInputStream(compressed.getInternal(), 0, compressed.size()))) {
				final byte[] buffer = new byte[4096];
				int count;
				while ((count = xzInputStream.read(buffer)) > 0) {
					unCompressed.write(buffer, 0, count);
				}
			}
			// the flush and close methods of OutputStream do nothing
			return unCompressed;
		}
	}
}
