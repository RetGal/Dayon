package mpo.dayon.common.squeeze;

import mpo.dayon.common.capture.CaptureTile;
import mpo.dayon.common.log.Log;

public class NullTileCache implements TileCache {
	public NullTileCache() {
		Log.info("NULL cache created");
	}

	@Override
	public int getCacheId(CaptureTile tile) {
		return 0;
	}

	@Override
	public void add(CaptureTile tile) {
		// noop
	}

	@Override
	public CaptureTile get(int cachedId) {
		return CaptureTile.MISSING;
	}

	@Override
	public int size() {
		return 0;
	}

	@Override
	public void clear() {
		// noop
	}

	@Override
	public void onCaptureProcessed() {
		// noop
	}

	@Override
	public void clearHits() {
		// noop
	}

	@Override
	public int getHits() {
		return 0;
	}
}