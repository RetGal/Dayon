package mpo.dayon.assisted.mouse;

import mpo.dayon.common.concurrent.RunnableEx;
import mpo.dayon.common.event.Listeners;
import mpo.dayon.common.log.Log;

import java.awt.*;
import java.util.Random;

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

    @SuppressWarnings("squid:S2189")
    private void mainLoop() throws InterruptedException {
        long start = System.currentTimeMillis();
        long lastMovement = start;
        int captureCount = 0;
        Point previous = new Point(-1, -1);

        //noinspection InfiniteLoopStatement
        while (true) {
            final PointerInfo pointerInfo = MouseInfo.getPointerInfo();
            // can happen if windows the ctrl + alt + delete screen is active
            if (pointerInfo != null) {
                final Point current = pointerInfo.getLocation();
                if (current.equals(previous) && System.currentTimeMillis() - lastMovement > 59000) {
                    moveMouse(current);
                }
                if (!current.equals(previous) && fireOnLocationUpdated(current)) {
                    previous = current;
                    lastMovement = System.currentTimeMillis();
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

    private void moveMouse(Point current) {
        int randX = new Random().nextInt(5) - 2;
        int randY = new Random().nextInt(3) - 1;
        current.translate(randX, randY);
        try {
            new Robot().mouseMove(current.x, current.y);
        } catch (AWTException e) {
            Log.error("Failed to move mouse", e);
        }
    }

    private boolean fireOnLocationUpdated(Point location) {
        return listeners.getListeners().stream().allMatch(listener -> listener.onLocationUpdated(location));
    }

}
