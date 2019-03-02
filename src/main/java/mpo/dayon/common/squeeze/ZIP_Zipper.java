package mpo.dayon.common.squeeze;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import mpo.dayon.common.buffer.MemByteBuffer;

public class ZIP_Zipper extends Zipper {
	public ZIP_Zipper() {
	}

	@Override
    public MemByteBuffer zip(MemByteBuffer unzipped) throws IOException {
		final MemByteBuffer zipped = new MemByteBuffer();

		final OutputStream zip = createZipOutputStream(zipped);

		zip.write(unzipped.getInternal(), 0, unzipped.size());
		zip.flush();

		zip.close();

		return zipped;
	}

	private static OutputStream createZipOutputStream(MemByteBuffer zipped) throws IOException {
		final ZipOutputStream zip = new ZipOutputStream(zipped);
		zip.putNextEntry(new ZipEntry("dirty-tiles"));
		return zip;
	}

	@Override
    public MemByteBuffer unzip(MemByteBuffer zipped) throws IOException {
		try (final MemByteBuffer unzipped = new MemByteBuffer()) {
			final InputStream unzip = createZipInputStream(zipped);

			final byte[] buffer = new byte[4096];

			int count;
			while ((count = unzip.read(buffer)) > 0) {
				unzipped.write(buffer, 0, count);
			}

			unzip.close();

			return unzipped;
		}
	}

	private static InputStream createZipInputStream(MemByteBuffer zipped) throws IOException {
		final ZipInputStream unzip = new ZipInputStream(new ByteArrayInputStream(zipped.getInternal(), 0, zipped.size()));
		unzip.getNextEntry();
		return unzip;
	}

}