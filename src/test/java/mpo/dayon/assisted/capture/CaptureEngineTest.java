package mpo.dayon.assisted.capture;

import mpo.dayon.common.capture.Capture;
import mpo.dayon.common.capture.CaptureEngineConfiguration;
import mpo.dayon.common.capture.CaptureTile;
import mpo.dayon.common.capture.Gray8Bits;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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

    @Test
    void capturesUntilTheCaptureFactoryIsExhausted() throws InterruptedException {
        // given
        final AtomicInteger calls = new AtomicInteger();
        final List<Capture> captures = Collections.synchronizedList(new ArrayList<>());
        final CountDownLatch latch = new CountDownLatch(2);
        final CaptureEngine engine = new CaptureEngine(new CaptureFactory() {
            @Override
            public Dimension getDimension() {
                return new Dimension(32, 32);
            }

            @Override
            public byte[] captureScreen(Gray8Bits quantization) {
                final int call = calls.getAndIncrement();
                if (call >= 2) {
                    return null;
                }
                final byte[] pixels = new byte[32 * 32];
                Arrays.fill(pixels, (byte) call);
                return pixels;
            }
        });
        engine.addListener(new CaptureEngineListener() {
            @Override
            public void onCaptured(Capture capture) {
                captures.add(capture);
                latch.countDown();
            }

            @Override
            public void onRawCaptured(int id, byte[] grays) {
                // debugging purpose
            }
        });
        engine.configure(new CaptureEngineConfiguration(200, Gray8Bits.X_128, false));

        // when
        engine.start();
        final boolean captured = latch.await(10, TimeUnit.SECONDS);
        engine.stop();

        // then
        assertTrue(captured, "two captures should have been taken");
        assertEquals(2, captures.size());
        assertTrue(captures.get(0).isReset(), "the first capture is always a reset one");
        assertFalse(captures.get(1).isReset());
        assertEquals(1, captures.get(0).getDirtyTileCount());
        assertEquals(1, captures.get(1).getDirtyTileCount());
    }

    @Test
    void canBeStoppedWhileCapturing() throws Exception {
        // given
        final CountDownLatch latch = new CountDownLatch(1);
        final CaptureEngine engine = new CaptureEngine(new CaptureFactory() {
            @Override
            public Dimension getDimension() {
                return new Dimension(32, 32);
            }

            @Override
            public byte[] captureScreen(Gray8Bits quantization) {
                return new byte[32 * 32 * 4];
            }
        });
        engine.addListener(new CaptureEngineListener() {
            @Override
            public void onCaptured(Capture capture) {
                latch.countDown();
            }

            @Override
            public void onRawCaptured(int id, byte[] grays) {
                // debugging purpose
            }
        });
        engine.configure(new CaptureEngineConfiguration(500, Gray8Bits.X_128, true));
        engine.start();
        assertTrue(latch.await(10, TimeUnit.SECONDS));

        // when
        engine.stop();

        // then
        final Field running = CaptureEngine.class.getDeclaredField("running");
        running.setAccessible(true);
        assertFalse(((AtomicBoolean) running.get(engine)).get());
        assertDoesNotThrow(engine::stop, "stopping twice must be possible");
    }
}
