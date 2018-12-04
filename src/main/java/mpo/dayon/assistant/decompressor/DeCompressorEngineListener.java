package mpo.dayon.assistant.decompressor;

import mpo.dayon.common.capture.Capture;
import mpo.dayon.common.event.Listener;

public interface DeCompressorEngineListener extends Listener {
	/**
	 * Called from within a de-compressor engine thread (!)
	 */
	void onDeCompressed(Capture capture, int cacheHits, double compressionRatio);
}
