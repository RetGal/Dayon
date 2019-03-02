package mpo.dayon.common.squeeze;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import com.sun.grizzly.lzma.compression.lzma.Decoder;
import com.sun.grizzly.lzma.compression.lzma.Encoder;
import mpo.dayon.common.buffer.MemByteBuffer;

public class LZMA_Zipper extends Zipper {

	@Override
    public MemByteBuffer zip(MemByteBuffer unzipped) throws IOException {
		final Encoder encoder = new Encoder();

		final InputStream inStream = new ByteArrayInputStream(unzipped.getInternal(), 0, unzipped.size());

		if (!encoder.SetDictionarySize(1 << 23)) {
			throw new IOException("LZMA: Incorrect dictionary size");
		}
		if (!encoder.SetNumFastBytes(128)) {
			throw new IOException("LZMA: Incorrect -fb value");
		}
		if (!encoder.SetMatchFinder(1)) {
			throw new IOException("LZMA: Incorrect -mf value");
		}
		if (!encoder.SetLcLpPb(3, 0, 2)) {
			throw new IOException("LZMA: Incorrect -lc or -lp or -pb value");
		}

		encoder.SetEndMarkerMode(false);

		final MemByteBuffer zipped = new MemByteBuffer();

		encoder.WriteCoderProperties(zipped);

		final long fileSize = unzipped.size();
		for (int i = 0; i < 8; i++) {
			zipped.write((int) (fileSize >>> (8 * i)) & 0xFF);
		}

		encoder.Code(inStream, zipped, -1, -1, null);

		inStream.close();

		return zipped;
	}

	@Override
    public MemByteBuffer unzip(MemByteBuffer zipped) throws IOException {
		final InputStream inStream = new ByteArrayInputStream(zipped.getInternal(), 0, zipped.size());

		final byte[] properties = new byte[5];
		if (inStream.read(properties, 0, properties.length) != properties.length) {
			throw new IOException("LZMA: input .lzma file is too short");
		}

		final Decoder decoder = new Decoder();
		if (!decoder.SetDecoderProperties(properties)) {
			throw new IOException("LZMA: Incorrect stream properties");
		}

		long outSize = 0;
		for (int i = 0; i < 8; i++) {
			int v = inStream.read();
			if (v < 0) {
				throw new IOException("LZMA: Can't read stream size");
			}
			outSize |= ((long) v) << (8 * i);
		}

		final MemByteBuffer unzipped = new MemByteBuffer();

		if (!decoder.Code(inStream, unzipped, outSize)) {
			throw new IOException("LZMA: Error in data stream");
		}

		inStream.close();

		return unzipped;
	}

}