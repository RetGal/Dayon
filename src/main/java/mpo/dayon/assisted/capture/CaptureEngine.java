package mpo.dayon.assisted.capture;

import java.awt.*;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import mpo.dayon.common.capture.Capture;
import mpo.dayon.common.capture.CaptureEngineConfiguration;
import mpo.dayon.common.capture.CaptureTile;
import mpo.dayon.common.capture.Gray8Bits;
import mpo.dayon.common.concurrent.RunnableEx;
import mpo.dayon.common.configuration.ReConfigurable;
import mpo.dayon.common.event.Listeners;
import mpo.dayon.common.gui.common.Position;
import mpo.dayon.common.log.Log;
import mpo.dayon.common.utils.UnitUtilities;

import static java.lang.Math.min;
import static java.lang.String.format;

public class CaptureEngine implements ReConfigurable<CaptureEngineConfiguration> {

    private static final Dimension TILE_DIMENSION = new Dimension(32, 32);

    private final Dimension captureDimension;

    private final CaptureFactory captureFactory;

    private final Listeners<CaptureEngineListener> listeners = new Listeners<>();

    private final Thread thread;

    /**
     * I keep only the checksum as I do not want to keep the referenceS to the
     * byte[] of the previous captureS.
     */
    private long[] previousCapture;

    private final Object reconfigurationLOCK = new Object();

    private CaptureEngineConfiguration configuration;

    private volatile boolean reconfigured;

    private boolean running;

    public CaptureEngine(CaptureFactory captureFactory) {
        this.captureFactory = captureFactory;
        this.captureDimension = captureFactory.getDimension();
        final int x = (captureDimension.width + TILE_DIMENSION.width -1) / TILE_DIMENSION.width;
        final int y = (captureDimension.height + TILE_DIMENSION.height -1) / TILE_DIMENSION.height;
        this.previousCapture = new long[x * y];
        resetPreviousCapture();
        running = true;

        this.thread = new Thread(new RunnableEx() {
            @Override
            protected void doRun() {
                CaptureEngine.this.mainLoop();
            }
        }, "CaptureEngine");
    }

    @Override
    public void configure(CaptureEngineConfiguration configuration) {
        synchronized (reconfigurationLOCK) {
            this.configuration = configuration;
            this.reconfigured = true;
        }
    }

    @Override
    public void reconfigure(CaptureEngineConfiguration configuration) {
        configure(configuration);
    }

    public void addListener(CaptureEngineListener listener) {
        listeners.add(listener);
        // We're keeping locally a previous state, so we must be sure to send at
        // least once the previous capture state to the new listener.
        synchronized (reconfigurationLOCK) {
            this.reconfigured = true;
        }
    }

    public void start() {
        Log.debug("CaptureEngine start");
        running = true;
        thread.start();
    }

    public void stop() {
        Log.debug("CaptureEngine stop");
        running = false;
        thread.interrupt();
        try {
            thread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void mainLoop() {
        Gray8Bits quantization = null;
        boolean captureColors = false;
        int tick = -1;
        long start = -1L;
        int captureId = 0;
        int captureCount = 0;
        int skipped = 0;
        AtomicBoolean reset = new AtomicBoolean(false);

        while (running) {
            if (reconfigured) {
                synchronized (reconfigurationLOCK) {
                    if (reconfigured) {
                        // assuming everything has changed (!)
                        quantization = configuration.getCaptureQuantization();
                        captureColors = configuration.isCaptureColors();
                        tick = configuration.getCaptureTick();
                        start = System.currentTimeMillis();
                        captureCount = 0;
                        skipped = 0;
                        resetPreviousCapture();
                        // I'm using a flag to tag the capture as a RESET - it is then easier
                        // to handle the reset message until the assistant without having to
                        // change anything (e.g., merging mechanism in the compressor engine).
                        reset.set(true);
                        Log.info(format("Capture engine has been reconfigured [tile: %d] %s", captureId, configuration));
                        reconfigured = false;
                    }
                }
            }

            final byte[] pixels = captureColors ? captureFactory.captureScreen(null) : captureFactory.captureScreen(quantization);
            if (pixels == null) {
                // testing purpose (!)
                Log.info("CaptureFactory has finished!");
                break;
            }

            ++captureCount;
            ++captureId;

            fireOnRawCaptured(captureId, pixels); // debugging purpose (!)
            final CaptureTile[] dirty = computeDirtyTiles(pixels);

            if (dirty != null) {
                final Capture capture = new Capture(captureId, reset.get(), skipped, 0, captureDimension, TILE_DIMENSION, dirty);
                fireOnCaptured(capture); // might update the capture (i.e., merging with previous not sent yet)
                updatePreviousCapture(capture);
                reset.set(false);
            }

            skipped = syncOnTick(start, captureCount, captureId, tick);
            if (skipped > 0) {
                captureCount += skipped;
                captureId += skipped;
                tick +=10;
                Log.info("Increased capture tick to " + tick);
            }
        }
        Log.info("The capture engine has been stopped!");
    }

    private static int syncOnTick(final long start, final int captureCount, final int captureId, final long tick) {
        int delayedCaptureCount = 0;
        while (true) {
            final long captureMaxEnd = start + (captureCount + delayedCaptureCount) * tick;
            final long capturePause = captureMaxEnd - System.currentTimeMillis();
            if (capturePause < 0L) {
                ++delayedCaptureCount;
                Log.warn(format("Skipping capture (%s) %s", captureId + delayedCaptureCount, UnitUtilities.toElapsedTime(-capturePause)));
            } else if (capturePause > 0L) {
                try {
                    TimeUnit.MILLISECONDS.sleep(capturePause);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return delayedCaptureCount;
            }
        }
    }

    private void resetPreviousCapture() {
        Arrays.fill(previousCapture, Long.MIN_VALUE);
    }

    private void updatePreviousCapture(Capture capture) {
        final CaptureTile[] dirtyTiles = capture.getDirtyTiles();
        for (int idx = 0; idx < dirtyTiles.length; idx++) {
            final CaptureTile dirtyTile = dirtyTiles[idx];
            if (dirtyTile != null) {
                previousCapture[idx] = dirtyTile.getChecksum();
            }
        }
    }

    private CaptureTile[] computeDirtyTiles(byte[] capture) {
        final int x = (captureDimension.width + TILE_DIMENSION.width - 1) / TILE_DIMENSION.width;
        final int y = (captureDimension.height + TILE_DIMENSION.height - 1) / TILE_DIMENSION.height;
        final int length = x * y;
        // change in screen resolution?
        if (length != previousCapture.length) {
            previousCapture = new long[length];
            resetPreviousCapture();
        }
        CaptureTile[] dirty = new CaptureTile[length];
        byte[] tileData;
        boolean hasDirty = false;
        int pixelSize = configuration.isCaptureColors() ? 4 : 1;
        int tileId = 0;
        for (int ty = 0; ty < captureDimension.height; ty += TILE_DIMENSION.height) {
            final int th = min(captureDimension.height - ty, TILE_DIMENSION.height);
            for (int tx = 0; tx < captureDimension.width; tx += TILE_DIMENSION.width) {
                final int tw = min(captureDimension.width - tx, TILE_DIMENSION.width);
                tileData = createTile(capture, captureDimension.width, tw, th, tx, ty, pixelSize);
                final long cs = CaptureTile.computeChecksum(tileData, 0, tileData.length);
                if (cs != previousCapture[tileId]) {
                    dirty[tileId] = new CaptureTile(cs, new Position(tx, ty), tw, th, tileData);
                    hasDirty = true;
                }
                ++tileId;
            }
        }
        return hasDirty ? dirty : null;
    }

    /**
     * Screen-rectangle buffer to tile-rectangle buffer. Use pixelSize 4 for colored and 1 for gray pixels.
     */
    private static byte[] createTile(byte[] capture, int width, int tw, int th, int tx, int ty, int pixelSize) {
        final int capacity = tw * th * pixelSize;
        final byte[] tile = new byte[capacity];
        final int maxSrcPos = capture.length;
        int srcPos = ty * width * pixelSize + tx * pixelSize;
        int destPos = 0;
        final int screenRowIncrement = width * pixelSize;
        final int tileRowIncrement = tw * pixelSize;
        while (destPos < capacity && srcPos < maxSrcPos) {
            System.arraycopy(capture, srcPos, tile, destPos, tileRowIncrement);
            srcPos += screenRowIncrement;
            destPos += tileRowIncrement;
        }
        return tile;
    }

    private void fireOnCaptured(Capture capture) {
        listeners.getListeners().forEach(listener -> listener.onCaptured(capture));
    }

    private void fireOnRawCaptured(int id, byte[] grays) {
        listeners.getListeners().forEach(listener -> listener.onRawCaptured(id, grays));
    }

}
