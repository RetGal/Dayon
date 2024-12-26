package mpo.dayon.common.monitoring;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import mpo.dayon.common.monitoring.counter.Counter;

public final class BigBrother {

    private BigBrother() {
    }

    private static final BigBrother INSTANCE = new BigBrother();

    public static BigBrother get() {
        return INSTANCE;
    }

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    /**
     * @param instantRatePeriod millis
     */
    public void registerCounter(Counter<?> counter, long instantRatePeriod) {
        scheduler.scheduleAtFixedRate(counter::computeAndResetInstantValue, 0, instantRatePeriod, TimeUnit.MILLISECONDS);
    }

    public void registerRamInfo(Runnable callback) {
        scheduler.scheduleAtFixedRate(callback, 0, 1291, TimeUnit.MILLISECONDS);
    }

    public void shutdown() {
        scheduler.shutdown();
    }
}