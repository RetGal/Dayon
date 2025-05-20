package mpo.dayon.common.squeeze;

import mpo.dayon.common.capture.CaptureTile;
import mpo.dayon.common.log.Log;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class RegularTileCache implements TileCache {

    private final Map<Integer, CaptureTile> tiles;
    private final int maxSize;
    private final int purgeSize;
    private final AtomicInteger hits = new AtomicInteger(0);

    public RegularTileCache(int maxSize, int purgeSize) {
        this.maxSize = maxSize;
        this.purgeSize = purgeSize;
        this.tiles = new LinkedHashMap<>(maxSize, 0.75f, true);
        Log.info("Regular cache created [MAX:" + maxSize + "][PURGE:" + purgeSize + "]");
    }

    @Override
    public synchronized void add(CaptureTile tile) {
        final int cacheId = getCacheId(tile);
        tile.incrementReferenceCount();
        tiles.put(cacheId, tile);
    }

    @Override
    public synchronized CaptureTile get(int cacheId) {
        final CaptureTile tile = tiles.get(cacheId);
        if (tile != null) {
            tile.incrementReferenceCount();
            hits.incrementAndGet();
            return tile;
        }
        return CaptureTile.MISSING;
    }

    /**
     * Called once a capture has been processed either in the assisted or in the
     * assistant side.
     * <p/>
     * Opportunity to remove unreferenced entries; not done during the processing of a
     * capture to keep references to cached tiles in the network messages consistent
     */
    @Override
    public synchronized void onCaptureProcessed() {
        if (tiles.size() >= maxSize) {
            Log.info("Purging the cache...");
            tiles.entrySet().removeIf(entry -> {
                CaptureTile tile = entry.getValue();
                tile.decrementReferenceCount();
                return tile.getReferenceCount() == 0 && tiles.size() > purgeSize;
            });
        }
    }

    @Override
    public int getCacheId(CaptureTile tile) {
        return (int) tile.getChecksum();
    }

    @Override
    public synchronized void clear() {
        Log.debug("Clearing the cache...");
        tiles.clear();
    }

    @Override
    public synchronized int size() {
        return tiles.size();
    }

    @Override
    public void clearHits() {
        hits.set(0);
    }

    @Override
    public int getHits() {
        return hits.get();
    }
}