package mpo.dayon.common.monitoring;

import java.util.Timer;
import java.util.TimerTask;

import mpo.dayon.common.monitoring.counter.Counter;

public final class BigBrother {

    private BigBrother() {
    }

    private static class Helper {
        private static final BigBrother INSTANCE = new BigBrother();
    }

    public static BigBrother get() {
        return Helper.INSTANCE;
    }

    private final Timer timer = new Timer("BigBrother");

    /**
     * @param instantRatePeriod millis
     */
    public void registerCounter(final Counter<?> counter, final long instantRatePeriod) {
        timer.scheduleAtFixedRate(new SecondsCounter(counter), 0, instantRatePeriod);
    }

    public void registerRamInfo(TimerTask callback) {
        timer.scheduleAtFixedRate(callback, 0, 1000);
    }

    private static class SecondsCounter extends TimerTask {
        private final Counter<?> counter;

        private SecondsCounter(Counter<?> counter) {
            this.counter = counter;
        }

        @Override
        public void run() {
            counter.computeAndResetInstantValue();
        }
    }
}
