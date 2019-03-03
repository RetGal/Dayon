package mpo.dayon.common.squeeze;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.itadaki.bzip2.BZip2InputStream;
import org.itadaki.bzip2.BZip2OutputStream;

import mpo.dayon.common.buffer.MemByteBuffer;

public class BZIP2_Zipper implements Zipper {

	@Override
    public MemByteBuffer zip(MemByteBuffer unzipped) throws IOException {
		final MemByteBuffer zipped = new MemByteBuffer();

		final OutputStream zip = createBZip2OutputStream(zipped);

		zip.write(unzipped.getInternal(), 0, unzipped.size());
		zip.flush();

		zip.close();

		return zipped;
	}

	private static OutputStream createBZip2OutputStream(MemByteBuffer zipped) throws IOException {
		return new BZip2OutputStream(zipped);
	}

	@Override
    public MemByteBuffer unzip(MemByteBuffer zipped) throws IOException {
		try (final MemByteBuffer unzipped = new MemByteBuffer()) {
			final InputStream unzip = createBZip2InputStream(zipped);

			final byte[] buffer = new byte[4096];

			int count;
			while ((count = unzip.read(buffer)) > 0) {
				unzipped.write(buffer, 0, count);
			}

			unzip.close();

			return unzipped;
		}
	}

	private static InputStream createBZip2InputStream(MemByteBuffer zipped) {
		return new BZip2InputStream(new ByteArrayInputStream(zipped.getInternal(), 0, zipped.size()), false);
	}

}