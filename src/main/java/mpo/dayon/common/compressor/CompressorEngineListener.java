package mpo.dayon.common.compressor;

import mpo.dayon.common.buffer.MemByteBuffer;
import mpo.dayon.common.event.Listener;
import mpo.dayon.common.squeeze.CompressionMethod;

public interface CompressorEngineListener extends Listener {
	/**
	 * May block (!)
	 */
	void onCompressed(int captureId, CompressionMethod compressionMethod, CompressorEngineConfiguration compressionConfiguration,
			MemByteBuffer compressed);

}