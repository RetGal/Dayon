package mpo.dayon.common.squeeze;

import mpo.dayon.common.capture.CaptureTile;
import mpo.dayon.common.gui.common.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RegularTileCacheTest {

    private RegularTileCache cache;

    @BeforeEach
    void setUp() {
        cache = new RegularTileCache(2, 1);
    }

    private CaptureTile tile(byte payload) {
        return new CaptureTile(1, payload, new Position(0, 0), 2, 2, new byte[]{payload});
    }

    @Test
    void addAndGet() {
        // given
        CaptureTile tile = tile((byte) 7);
        cache.add(tile);

        // when
        CaptureTile cached = cache.get(cache.getCacheId(tile));

        // then
        assertSame(tile, cached);
        assertEquals(1, cache.size());
        assertEquals(1, cache.getHits());
    }

    @Test
    void getUnknownTileReturnsMissing() {
        // given, when
        CaptureTile cached = cache.get(123456);

        // then
        assertSame(CaptureTile.MISSING, cached);
        assertEquals(0, cache.getHits());
    }

    @Test
    void getReferencedTileIsKeptOnPurge() {
        // given
        CaptureTile kept = tile((byte) 1);
        CaptureTile unreferenced = tile((byte) 2);
        cache.add(kept);
        cache.add(unreferenced);
        cache.get(cache.getCacheId(kept)); // increments the reference count

        // when
        cache.onCaptureProcessed();

        // then
        assertSame(kept, cache.get(cache.getCacheId(kept)));
        assertSame(CaptureTile.MISSING, cache.get(cache.getCacheId(unreferenced)), "unreferenced tiles are purged");
    }

    @Test
    void purgeIsSkippedWhenCacheIsNotFull() {
        // given
        CaptureTile tile = tile((byte) 3);
        cache.add(tile);

        // when
        cache.onCaptureProcessed();

        // then
        assertEquals(1, cache.size());
        assertSame(tile, cache.get(cache.getCacheId(tile)));
    }

    @Test
    void clearResetsSizeAndKeepsHitsCounting() {
        // given
        CaptureTile tile = tile((byte) 4);
        cache.add(tile);

        // when
        cache.clear();
        cache.clearHits();

        // then
        assertEquals(0, cache.size());
        assertEquals(0, cache.getHits());
        assertSame(CaptureTile.MISSING, cache.get(cache.getCacheId(tile)));
    }
}
