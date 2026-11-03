package mpo.dayon.common.squeeze;

import mpo.dayon.common.capture.CaptureTile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NullTileCacheTest {

    @Test
    void theNullCacheNeverHoldsAnything() {
        // given
        NullTileCache cache = new NullTileCache();

        // when, then
        cache.add(CaptureTile.MISSING);
        assertEquals(0, cache.getCacheId(CaptureTile.MISSING));
        assertSame(CaptureTile.MISSING, cache.get(1));
        assertEquals(0, cache.size());
        assertEquals(0, cache.getHits());
        cache.onCaptureProcessed();
        cache.clear();
        cache.clearHits();
        assertEquals(0, cache.getHits());
    }
}
