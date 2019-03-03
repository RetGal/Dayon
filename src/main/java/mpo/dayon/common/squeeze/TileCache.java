package mpo.dayon.common.squeeze;

import mpo.dayon.common.capture.CaptureTile;

public interface TileCache {

	int getCacheId(CaptureTile tile);

	void add(CaptureTile tile);

	CaptureTile get(int cachedId);

	int size();

	void clear();

	/**
	 * Called once a capture has been processed either in the assisted or in the
	 * assistant side.
	 */
	void onCaptureProcessed();

	void clearHits();

	int getHits();
}
