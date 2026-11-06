package mpo.dayon.common.compressor;

import mpo.dayon.common.buffer.MemByteBuffer;
import mpo.dayon.common.capture.Capture;
import mpo.dayon.common.capture.CaptureTile;
import mpo.dayon.common.gui.common.Position;
import mpo.dayon.common.squeeze.CompressionMethod;
import mpo.dayon.common.squeeze.Compressor;
import mpo.dayon.common.squeeze.NullTileCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class CompressorEngineTest {

    private static final int SIZE = 32;

    private CompressorEngine engine;

    @AfterEach
    void tearDown() {
        if (engine != null) {
            engine.stop();
            engine = null;
        }
    }

    private static Capture capture(int id) {
        return capture(id, true);
    }

    private static Capture capture(int id, boolean reset) {
        final byte[] data = new byte[SIZE * SIZE];
        Arrays.fill(data, (byte) (id % 2 == 0 ? 1 : 2));
        final CaptureTile tile = new CaptureTile(0, id, new Position(0, 0), SIZE, SIZE, data);
        return new Capture(id, reset, 0, 0, new Dimension(SIZE, SIZE), new Dimension(SIZE, SIZE), new CaptureTile[] { tile });
    }

    @Test
    void compressesCapturesAndSendsTheConfigurationAfterAReconfiguration() throws Exception {
        // given
        engine = new CompressorEngine();
        final List<Integer> captureIds = new CopyOnWriteArrayList<>();
        final List<CompressorEngineConfiguration> configurations = new CopyOnWriteArrayList<>();
        final List<MemByteBuffer> payloads = new CopyOnWriteArrayList<>();
        final CountDownLatch latch = new CountDownLatch(2);
        engine.addListener((captureId, compressionMethod, compressionConfiguration, compressed) -> {
            captureIds.add(captureId);
            configurations.add(compressionConfiguration);
            payloads.add(compressed);
            latch.countDown();
        });
        engine.configure(new CompressorEngineConfiguration(CompressionMethod.ZIP, false, 1024, 512));
        engine.start(10);

        // when
        engine.onCaptured(capture(1, false));
        engine.onCaptured(capture(2, false));

        // then
        assertTrue(latch.await(5, TimeUnit.SECONDS), "the captures should be compressed");
        assertEquals(Arrays.asList(1, 2), captureIds);
        assertNotNull(configurations.get(0), "the first capture carries the configuration");
        assertNull(configurations.get(1), "subsequent captures reuse the configuration");

        final Capture decompressed = Compressor.get(CompressionMethod.ZIP).decompress(new NullTileCache(), payloads.get(0));
        assertEquals(1, decompressed.getId());
        assertEquals(1, decompressed.getDirtyTileCount());
    }

    @Test
    void resetCaptureCarriesConfigurationEvenWithoutReconfiguration() throws Exception {
        // given
        engine = new CompressorEngine();
        final List<CompressorEngineConfiguration> configurations = new CopyOnWriteArrayList<>();
        final List<MemByteBuffer> payloads = new CopyOnWriteArrayList<>();
        final CountDownLatch firstLatch = new CountDownLatch(1);
        final CountDownLatch secondLatch = new CountDownLatch(1);
        engine.addListener((captureId, compressionMethod, compressionConfiguration, compressed) -> {
            configurations.add(compressionConfiguration);
            payloads.add(compressed);
            if (captureId == 1) {
                firstLatch.countDown();
            } else {
                secondLatch.countDown();
            }
        });
        engine.configure(new CompressorEngineConfiguration(CompressionMethod.ZIP, true, 1024, 512));
        engine.start(10);
        engine.onCaptured(capture(1, false));
        assertTrue(firstLatch.await(5, TimeUnit.SECONDS), "the initial capture should be compressed");

        // when
        engine.onCaptured(capture(2, true));

        // then
        assertTrue(secondLatch.await(5, TimeUnit.SECONDS), "the reset capture should be compressed");
        assertNotNull(configurations.get(0));
        assertNotNull(configurations.get(1), "reset captures carry the cache configuration");
        final Capture decompressed = decompress(CompressionMethod.ZIP, payloads.get(1));
        assertEquals(2, decompressed.getId());
        assertTrue(decompressed.isReset());
    }

    @Test
    void compressesCapturesAfterAReconfiguration() throws Exception {
        // given
        engine = new CompressorEngine();
        final List<CompressorEngineConfiguration> configurations = new CopyOnWriteArrayList<>();
        final CountDownLatch firstLatch = new CountDownLatch(1);
        final CountDownLatch secondLatch = new CountDownLatch(1);
        engine.addListener((captureId, compressionMethod, compressionConfiguration, compressed) -> {
            configurations.add(compressionConfiguration);
            if (captureId == 1) {
                firstLatch.countDown();
            } else {
                secondLatch.countDown();
            }
        });
        engine.configure(new CompressorEngineConfiguration(CompressionMethod.ZIP, true, 1024, 512));
        engine.start(10);
        engine.onCaptured(capture(1));
        assertTrue(firstLatch.await(5, TimeUnit.SECONDS));

        // when
        engine.reconfigure(new CompressorEngineConfiguration(CompressionMethod.XZ, false, 1024, 512));
        engine.onCaptured(capture(2));

        // then
        assertTrue(secondLatch.await(5, TimeUnit.SECONDS));
        assertEquals(2, configurations.size());
        assertNotNull(configurations.get(1));
        assertEquals(CompressionMethod.XZ, configurations.get(1).getMethod());
    }

    @Test
    void resetDropsPendingCapturesAndCompletesWhenReconnectThreadIsInterrupted() throws Exception {
        // given
        engine = new CompressorEngine();
        final List<Integer> captureIds = new CopyOnWriteArrayList<>();
        final List<CompressorEngineConfiguration> configurations = new CopyOnWriteArrayList<>();
        final CountDownLatch firstCaptureStarted = new CountDownLatch(1);
        final CountDownLatch releaseFirstCapture = new CountDownLatch(1);
        final CountDownLatch capturesCompleted = new CountDownLatch(2);
        engine.addListener((captureId, compressionMethod, compressionConfiguration, compressed) -> {
            captureIds.add(captureId);
            configurations.add(compressionConfiguration);
            capturesCompleted.countDown();
            if (captureId == 1) {
                firstCaptureStarted.countDown();
                try {
                    releaseFirstCapture.await();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }
            }
        });
        engine.configure(new CompressorEngineConfiguration(CompressionMethod.ZIP, true, 1024, 512));
        engine.start(10);
        engine.onCaptured(capture(1));
        assertTrue(firstCaptureStarted.await(5, TimeUnit.SECONDS));

        // when
        engine.onCaptured(capture(2, false));
        final CountDownLatch resetReturned = new CountDownLatch(1);
        final Thread resetThread = new Thread(() -> {
            engine.reset();
            resetReturned.countDown();
        });
        resetThread.start();

        // then
        try {
            assertFalse(resetReturned.await(100, TimeUnit.MILLISECONDS), "reset waits for the active capture");
            resetThread.interrupt();
        } finally {
            releaseFirstCapture.countDown();
        }
        assertTrue(resetReturned.await(5, TimeUnit.SECONDS));
        resetThread.join();
        assertFalse(resetThread.isInterrupted(), "the reconnect thread must clear its stale interruption");
        engine.onCaptured(capture(3, false));
        engine.resumeAfterReset();
        engine.onCaptured(capture(4, false));
        engine.onCaptured(capture(5, true));

        assertTrue(capturesCompleted.await(5, TimeUnit.SECONDS));
        assertEquals(Arrays.asList(1, 5), captureIds);
        assertNotNull(configurations.get(1), "the first capture after reset carries the configuration");
    }

    @Test
    void resetTimesOutWhenTheCompressorIsWedged() throws Exception {
        // given
        engine = new CompressorEngine();
        final CountDownLatch firstCaptureStarted = new CountDownLatch(1);
        final CountDownLatch releaseFirstCapture = new CountDownLatch(1);
        engine.addListener((captureId, compressionMethod, compressionConfiguration, compressed) -> {
            if (captureId == 1) {
                firstCaptureStarted.countDown();
                try {
                    releaseFirstCapture.await();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        engine.configure(new CompressorEngineConfiguration(CompressionMethod.ZIP, true, 1024, 512));
        engine.start(10);
        engine.onCaptured(capture(1));
        assertTrue(firstCaptureStarted.await(5, TimeUnit.SECONDS));

        // when
        final CountDownLatch resetReturned = new CountDownLatch(1);
        final Thread resetThread = new Thread(() -> {
            engine.reset();
            resetReturned.countDown();
        });
        resetThread.start();

        // then
        try {
            assertTrue(resetReturned.await(5, TimeUnit.SECONDS),
                    "reset must not block the reconnect forever when the compressor is wedged");
        } finally {
            releaseFirstCapture.countDown();
            resetThread.join(5_000);
        }
    }

    private static Capture decompress(CompressionMethod method, MemByteBuffer compressed) {
        try {
            return Compressor.get(method).decompress(new NullTileCache(), compressed);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @Test
    void mergesPendingCapturesWhenTheQueueIsFull() throws Exception {
        // given
        engine = new CompressorEngine();
        final List<Capture> decompressed = new CopyOnWriteArrayList<>();
        final CountDownLatch latch = new CountDownLatch(1);
        engine.addListener((captureId, compressionMethod, compressionConfiguration, compressed) -> {
            decompressed.add(decompress(compressionMethod, compressed));
            latch.countDown();
        });
        engine.configure(new CompressorEngineConfiguration(CompressionMethod.ZIP, false, 1024, 512));
        engine.start(1);

        // when
        for (int idx = 1; idx <= 100; idx++) {
            engine.onCaptured(capture(idx));
        }

        // then
        assertTrue(latch.await(5, TimeUnit.SECONDS), "at least one capture should be compressed");
        assertFalse(decompressed.isEmpty());
        assertTrue(decompressed.stream().allMatch(capture -> capture.getDirtyTileCount() == 1));
    }

    @Test
    void onRawCapturedIsIgnored() {
        // given
        engine = new CompressorEngine();
        engine.configure(new CompressorEngineConfiguration(CompressionMethod.ZIP, false, 1024, 512));
        engine.start(1);

        // when, then
        assertDoesNotThrow(() -> engine.onRawCaptured(1, new byte[] { 1, 2, 3 }));
    }
}
