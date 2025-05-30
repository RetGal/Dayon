package mpo.dayon.common.squeeze;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import mpo.dayon.common.buffer.MemByteBuffer;

public class ZipZipper implements Zipper {

	@Override
    public MemByteBuffer zip(MemByteBuffer unzipped) throws IOException {
		MemByteBuffer zipped = MemByteBuffer.acquire(unzipped.size());
		try (OutputStream zip = createZipOutputStream(zipped)) {
			zip.write(unzipped.getInternal(), 0, unzipped.size());
		} finally {
			unzipped.release();
		}
		return zipped;
	}

	private static OutputStream createZipOutputStream(MemByteBuffer zipped) throws IOException {
		final ZipOutputStream zip = new ZipOutputStream(zipped);
		zip.putNextEntry(new ZipEntry("dirty-tiles"));
		return zip;
	}

	@Override
    public MemByteBuffer unzip(MemByteBuffer zipped) throws IOException {
		MemByteBuffer unzipped = MemByteBuffer.acquire();
		try (InputStream unzip = createZipInputStream(zipped)) {
			final byte[] buffer = new byte[4096];
			int count;
			while ((count = unzip.read(buffer)) > 0) {
				unzipped.write(buffer, 0, count);
			}
        } finally {
			zipped.release();
		}
		return unzipped;
	}

	private static InputStream createZipInputStream(MemByteBuffer zipped) throws IOException {
		final ZipInputStream unzip = new ZipInputStream(new ByteArrayInputStream(zipped.getInternal(), 0, zipped.size()));
		unzip.getNextEntry();
		return unzip;
	}
}