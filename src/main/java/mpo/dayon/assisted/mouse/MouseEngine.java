package mpo.dayon.assisted.mouse;

import mpo.dayon.common.concurrent.RunnableEx;
import mpo.dayon.common.event.Listeners;
import mpo.dayon.common.log.Log;

import java.awt.*;

public class MouseEngine {
    private final Listeners<MouseEngineListener> listeners = new Listeners<>();
    private final Thread thread;

    public MouseEngine(MouseEngineListener listener) {
        listeners.add(listener);
        thread = new Thread(new RunnableEx() {
            @Override
            protected void doRun() {
                try {
                    MouseEngine.this.mainLoop();
                } catch (InterruptedException e) {
                    thread.interrupt();
                }
            }
        }, "MouseEngine");
    }

    public void start() {
        Log.debug("MouseEngine start");
        thread.start();
    }

    public void stop() {
        Log.debug("MouseEngine stop");
        thread.interrupt();
    }

    @java.lang.SuppressWarnings("squid:S2189")
    private void mainLoop() throws InterruptedException {
        long start = System.currentTimeMillis();
        int captureCount = 0;
        Point previous = new Point(-1, -1);

        //noinspection InfiniteLoopStatement
        while (true) {
            final PointerInfo pointerInfo = MouseInfo.getPointerInfo();
            // can happen if windows the ctrl + alt + delete screen is active
            if (pointerInfo != null) {
                final Point current = pointerInfo.getLocation();
                if (!current.equals(previous) && fireOnLocationUpdated(current)) {
                    previous = current;
                }
            }
            ++captureCount;
            captureCount += syncOnTick(start, captureCount);
        }
    }

    private static int syncOnTick(final long start, final int captureCount) throws InterruptedException {
        int delayedCaptureCount = 0;
        while (true) {
            final long captureMaxEnd = start + (captureCount + delayedCaptureCount) * 50L;
            final long capturePause = captureMaxEnd - System.currentTimeMillis();
            if (capturePause < 0) {
                ++delayedCaptureCount;
            } else if (capturePause > 0) {
                Thread.sleep(capturePause);
                return delayedCaptureCount;
            }
        }
    }

    private boolean fireOnLocationUpdated(Point location) {
        return listeners.getListeners().stream().allMatch(listener -> listener.onLocationUpdated(location));
    }

}
