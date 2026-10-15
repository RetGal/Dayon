package mpo.dayon.assisted.capture;

import mpo.dayon.common.capture.Capture;
import mpo.dayon.common.capture.CaptureEngineConfiguration;
import mpo.dayon.common.capture.CaptureTile;
import mpo.dayon.common.capture.Gray8Bits;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class CaptureEngineTest {

    @Test
    void testAddListenerTriggersFullCaptureResetWhenConfigured() throws Exception {
        // given
        CaptureEngine engine = new CaptureEngine(new CaptureFactory() {
            @Override
            public Dimension getDimension() {
                return new Dimension(32, 32);
            }

            @Override
            public byte[] captureScreen(Gray8Bits quantization) {
                return new byte[32 * 32];
            }
        });

        // when
        engine.configure(new CaptureEngineConfiguration(200, Gray8Bits.X_128, false));
        engine.addListener(mock(CaptureEngineListener.class));
        Field reconfiguredField = CaptureEngine.class.getDeclaredField("reconfigured");
        reconfiguredField.setAccessible(true);

        // then
        assertTrue(((AtomicBoolean) reconfiguredField.get(engine)).get());
    }

    @Test
    void testComputeDirtyTilesOnlyCopiesChangedTilesIncludingScreenEdges() throws Exception {
        // given
        CaptureEngine engine = new CaptureEngine(new CaptureFactory() {
            @Override
            public Dimension getDimension() {
                return new Dimension(33, 33);
            }

            @Override
            public byte[] captureScreen(Gray8Bits quantization) {
                return new byte[33 * 33];
            }
        });
        engine.configure(new CaptureEngineConfiguration(200, Gray8Bits.X_128, false));
        Method computeDirtyTiles = CaptureEngine.class.getDeclaredMethod("computeDirtyTiles", byte[].class, int.class);
        computeDirtyTiles.setAccessible(true);
        byte[] pixels = new byte[33 * 33];

        CaptureTile[] initialTiles = (CaptureTile[]) computeDirtyTiles.invoke(engine, (Object) pixels, 1);
        assertNotNull(initialTiles);
        Method updatePreviousCapture = CaptureEngine.class.getDeclaredMethod("updatePreviousCapture", Capture.class);
        updatePreviousCapture.setAccessible(true);
        updatePreviousCapture.invoke(engine, new Capture(1, false, 0, 0, new Dimension(33, 33), new Dimension(32, 32), initialTiles));
        assertNull(computeDirtyTiles.invoke(engine, (Object) pixels, 1));

        // when
        pixels[32 * 33 + 32] = 7;
        CaptureTile[] dirtyTiles = (CaptureTile[]) computeDirtyTiles.invoke(engine, (Object) pixels, 1);

        // then
        assertNotNull(dirtyTiles);
        assertEquals(4, dirtyTiles.length);
        assertNull(dirtyTiles[0]);
        assertNull(dirtyTiles[1]);
        assertNull(dirtyTiles[2]);
        assertEquals(32, dirtyTiles[3].getX());
        assertEquals(32, dirtyTiles[3].getY());
        assertArrayEquals(new byte[] { 7 }, dirtyTiles[3].getCapture().getInternal());
    }
}
