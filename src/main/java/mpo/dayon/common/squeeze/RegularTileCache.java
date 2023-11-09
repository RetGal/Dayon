package mpo.dayon.common.squeeze;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import mpo.dayon.common.capture.CaptureTile;
import mpo.dayon.common.log.Log;

public class RegularTileCache implements TileCache {
    /**
     * Maximum number of tiles; currently a tile is basically a 32x32 byte array (i.e., 1K).
     */
    public static final int DEFAULT_MAX_SIZE = 16 * 1024;

    /**
     * Number of tiles after a purge.
     */
    public static final int DEFAULT_PURGE_SIZE = 14 * 1024;

    private final Map<Integer, CaptureTile> tiles = new HashMap<>();

    private final LinkedList<Integer> lru = new LinkedList<>();

    private final int maxSize;

    private final int purgeSize;

    private int hits;

    public RegularTileCache(int maxSize, int purgeSize) {
        this.maxSize = maxSize;
        this.purgeSize = purgeSize;
        Log.info("Regular cache created [MAX:" + maxSize + "][PURGE:" + purgeSize + "]");
    }

    @Override
    public int getCacheId(CaptureTile tile) {
        final long cs = tile.getChecksum();
        if (cs < 0 || cs > 4294967295L) {
            throw new IllegalStateException("Ouch [" + cs + "]");
        }
        return (int) cs;
    }

    @Override
    public void add(CaptureTile tile) {
        if (tiles.size() < maxSize) {
            final Integer cacheId = getCacheId(tile);
            tiles.put(cacheId, tile);
            lru.addFirst(cacheId);
        }
    }

    @Override
    public CaptureTile get(int cacheId) {
        final Integer xcacheId = cacheId;
        final CaptureTile tile = tiles.get(xcacheId);
        if (tile != null) {
            ++hits;
            lru.addFirst(xcacheId);
            return tile;
        }
        return CaptureTile.MISSING;
    }

    @Override
    public int size() {
        return tiles.size();
    }

    @Override
    public void clear() {
        Log.info("Clearing the cache...");
        tiles.clear();
        lru.clear();
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
        if (!tiles.isEmpty() && tiles.size() >= maxSize) {
            Log.info("Purging the cache...");
            while (tiles.size() > purgeSize) {
                tiles.remove(lru.removeFirst());
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