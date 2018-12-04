package mpo.dayon.common.squeeze;

import mpo.dayon.common.capture.CaptureTile;

public abstract class TileCache {
	TileCache() {
	}

	public abstract int getCacheId(CaptureTile tile);

	public abstract void add(CaptureTile tile);

	public abstract CaptureTile get(int cachedId);

	public abstract int size();

	public abstract void clear();

	/**
	 * Called once a capture has been processed either in the assisted or in the
	 * assistant side.
	 */
	public abstract void onCaptureProcessed();

	public abstract void clearHits();

	public abstract int getHits();
}
