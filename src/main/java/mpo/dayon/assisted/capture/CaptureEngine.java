package mpo.dayon.assisted.capture;

import java.awt.*;
import java.util.Arrays;
import java.util.Objects;
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

    private boolean reconfigured;

    public CaptureEngine(CaptureFactory captureFactory) {
        this.captureFactory = captureFactory;
        final int x = (int) Math.ceil((float) captureFactory.getDimension().width / TILE_DIMENSION.width);
        final int y = (int) Math.ceil((float) captureFactory.getDimension().height / TILE_DIMENSION.height);
        this.previousCapture = new long[x * y];
        resetPreviousCapture();

        this.thread = new Thread(new RunnableEx() {
            @Override
            protected void doRun() {
                try {
                    CaptureEngine.this.mainLoop();
                } catch (InterruptedException e) {
                    thread.interrupt();
                }
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
        thread.start();
    }

    public void stop() {
        Log.debug("CaptureEngine stop");
        thread.interrupt();
    }

    private void mainLoop() throws InterruptedException {
        Gray8Bits quantization = null;
        int tick = -1;
        long start = -1L;
        int captureId = 0;
        int captureCount = 0;
        int skipped = 0;
        AtomicBoolean reset = new AtomicBoolean(false);

        while (true) {
            reset.set(false);
            synchronized (reconfigurationLOCK) {
                if (reconfigured) {
                    // assuming everything has changed (!)
                    quantization = configuration.getCaptureQuantization();
                    tick = configuration.getCaptureTick();
                    start = System.currentTimeMillis();
                    captureCount = 0;
                    skipped = 0;
                    resetPreviousCapture();
                    // I'm using a flag to tag the capture as a RESET - it is then easier
                    // to handle the reset message until the assistant without having to
                    // change anything (e.g., merging mechanism in the compressor engine).
                    reset.set(true);
                    Log.info(format("Capture engine has been reconfigured [tile: %d] %s", captureId , configuration));
                    reconfigured = false;
                }
            }
            ++captureCount;
            ++captureId;
            final byte[] pixels = captureFactory.captureGray(quantization);

            if (pixels == null) {
                // testing purpose (!)
                Log.info("CaptureFactory has finished!");
                break;
            }
            fireOnRawCaptured(captureId, pixels); // debugging purpose (!)
            final CaptureTile[] dirty = computeDirtyTiles(captureId, pixels, captureFactory.getDimension());

            if (!Arrays.stream(dirty).allMatch(Objects::isNull)) {
                final Capture capture = new Capture(captureId, reset.get(), skipped, 0, captureFactory.getDimension(), TILE_DIMENSION, dirty);
                fireOnCaptured(capture); // might update the capture (i.e., merging with previous not sent yet)
                updatePreviousCapture(capture);
            }

            final int delayedCaptureCount = syncOnTick(start, captureCount, captureId, tick);
            captureCount += delayedCaptureCount;
            captureId += delayedCaptureCount;
            skipped = delayedCaptureCount;
        }
        Log.info("The capture engine has been stopped!");
    }

    private static int syncOnTick(final long start, final int captureCount, final int captureId, final long tick) throws InterruptedException {
        int delayedCaptureCount = 0;
        while (true) {
            final long captureMaxEnd = start + (captureCount + delayedCaptureCount) * tick;
            final long capturePause = captureMaxEnd - System.currentTimeMillis();
            if (capturePause < 0) {
                ++delayedCaptureCount;
                Log.warn(format("Skipping capture (%d) %s", captureId + delayedCaptureCount, UnitUtilities.toElapsedTime(-capturePause)));
            } else if (capturePause > 0) {
                Thread.sleep(capturePause);
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

    private CaptureTile[] computeDirtyTiles(int captureId, byte[] capture, Dimension captureDimension) {
        final int x = (int) Math.ceil((float) captureDimension.width / TILE_DIMENSION.width);
        final int y = (int) Math.ceil((float) captureDimension.height / TILE_DIMENSION.height);
        final int length = x * y;
        // change in screen resolution?
        if (length != previousCapture.length) {
            previousCapture = new long[length];
            resetPreviousCapture();
        }
        CaptureTile[] dirty = new CaptureTile[length];
        int tileId = 0;
        for (int ty = 0; ty < captureDimension.height; ty += TILE_DIMENSION.height) {
            final int th = min(captureDimension.height - ty, TILE_DIMENSION.height);
            for (int tx = 0; tx < captureDimension.width; tx += TILE_DIMENSION.width) {
                final int tw = min(captureDimension.width - tx, TILE_DIMENSION.width);
                final int offset = ty * captureDimension.width + tx;
                final byte[] tileData = createTile(capture, captureDimension.width, offset, tw, th);
                final long cs = CaptureTile.computeChecksum(tileData, 0, tileData.length);
                if (cs != previousCapture[tileId]) {
                    final Position position = new Position(tx, ty);
                    dirty[tileId] = new CaptureTile(captureId, tileId, cs, position, tw, th, tileData);
                }
                ++tileId;
            }
        }
        return dirty;
    }

    /**
     * Screen-rectangle buffer to tile-rectangle buffer.
     */
    private static byte[] createTile(byte[] capture, int width, int srcPos, int tw, int th) {
        final int capacity = tw * th;
        final byte[] tile = new byte[capacity];
        final int maxSrcPos = capture.length;
        int destPos = 0;

        while (destPos < capacity && srcPos < maxSrcPos) {
            System.arraycopy(capture, srcPos, tile, destPos, tw);
            srcPos += width;
            destPos += tw;
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
