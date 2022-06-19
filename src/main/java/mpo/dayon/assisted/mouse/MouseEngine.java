package mpo.dayon.assisted.mouse;

import java.awt.MouseInfo;
import java.awt.Point;
import java.util.List;

import mpo.dayon.common.concurrent.RunnableEx;
import mpo.dayon.common.event.Listeners;
import mpo.dayon.common.log.Log;

public class MouseEngine {
    private final Listeners<MouseEngineListener> listeners = new Listeners<>();

    private final Thread thread;

    public MouseEngine() {
        this.thread = new Thread(new RunnableEx() {
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

    public void addListener(MouseEngineListener listener) {
        listeners.add(listener);
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
            final Point current = MouseInfo.getPointerInfo().getLocation();

            ++captureCount;

            if (!current.equals(previous) && fireOnLocationUpdated(current)) {
                previous = current;
            }

            final int delayedCaptureCount = syncOnTick(start, captureCount);

            captureCount += delayedCaptureCount;
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
                break;
            }
        }

        return delayedCaptureCount;
    }

    private boolean fireOnLocationUpdated(Point location) {
        final List<MouseEngineListener> xListeners = listeners.getListeners();
        return xListeners.stream().allMatch(xListener -> xListener.onLocationUpdated(location));
    }

}
