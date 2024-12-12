package mpo.dayon.common.squeeze;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import mpo.dayon.common.capture.CaptureTile;
import mpo.dayon.common.log.Log;

import static java.lang.String.format;

public class RegularTileCache implements TileCache {

    private final Map<Integer, CaptureTile> tiles;
    private final int maxSize;
    private final int purgeSize;
    private int hits = 0;

    public RegularTileCache(int maxSize, int purgeSize) {
        this.maxSize = maxSize;
        this.purgeSize = purgeSize;
        tiles = new LinkedHashMap<>(maxSize, 0.75f, true) {
            @Override
            public CaptureTile get(Object key) {
                CaptureTile tile = super.get(key);
                if (tile != null) {
                    ++hits;
                }
                return tile != null ? tile : CaptureTile.MISSING;
            }
        };
        Log.info("Regular cache created [MAX:" + maxSize + "][PURGE:" + purgeSize + "]");
    }

    @Override
    public int getCacheId(CaptureTile tile) {
        long cs = tile.getChecksum();
        if (cs < 0L || cs > 4294967295L) {
            Log.warn(format("CacheId %d truncated to %d", cs , (int) cs));
        }
        return (int) cs;
    }

    @Override
    public void add(CaptureTile tile) {
        tiles.put(getCacheId(tile), tile);
    }

    @Override
    public CaptureTile get(int cacheId) {
        return tiles.get(cacheId);
    }

    @Override
    public int size() {
        return tiles.size();
    }

    @Override
    public void clear() {
        Log.debug("Clearing the cache...");
        tiles.clear();
        hits = 0;
    }

    /**
     * Called once a capture has been processed either in the assisted or in the
     * assistant side.
     * <p/>
     * Opportunity to remove oldest entries; not done during the processing of a
     * capture to keep references to cached tiles in the network messages
     * consistent - easier to debug this way I guess ...
     */
    @Override
    public void onCaptureProcessed() {
        if (tiles.size() >= maxSize) {
            Log.info("Purging the cache...");
            int numToRemove = tiles.size() - purgeSize;
            Iterator<Map.Entry<Integer, CaptureTile>> iterator = tiles.entrySet().iterator();
            for (int i = 0; i < numToRemove; i++) {
                iterator.next(); // move to the next entry
                iterator.remove(); // remove the entry
            }
        }
    }

    @Override
    public void clearHits() {
        hits = 0;
    }

    @Override
    public int getHits() {
        return hits;
    }
}