package mpo.dayon.assisted.compressor;

import org.jetbrains.annotations.Nullable;

import mpo.dayon.common.buffer.MemByteBuffer;
import mpo.dayon.common.capture.Capture;
import mpo.dayon.common.event.Listener;
import mpo.dayon.common.squeeze.CompressionMethod;

public interface CompressorEngineListener extends Listener {
	/**
	 * May block (!)
	 */
	void onCompressed(Capture capture, CompressionMethod compressionMethod, @Nullable CompressorEngineConfiguration compressionConfiguration,
			MemByteBuffer compressed);

}