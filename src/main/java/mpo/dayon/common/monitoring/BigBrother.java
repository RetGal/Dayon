package mpo.dayon.common.monitoring;

import java.util.Timer;
import java.util.TimerTask;

import mpo.dayon.common.monitoring.counter.Counter;

public class BigBrother {
    private static final BigBrother INSTANCE = new BigBrother();

    private final Timer timer = new Timer("BigBrother");

    private BigBrother() {
    }

    public static BigBrother get() {
        return INSTANCE;
    }

    /**
     * @param instantRatePeriod millis
     */
    public void registerCounter(final Counter<?> counter, final long instantRatePeriod) {
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                counter.computeAndResetInstantValue();
            }
        }, 0, instantRatePeriod);
    }

    public void registerRamInfo(TimerTask callback) {
        timer.scheduleAtFixedRate(callback, 0, 1000);
    }
}
