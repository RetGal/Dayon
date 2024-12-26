package mpo.dayon.common.monitoring;

import mpo.dayon.common.monitoring.counter.Counter;

public interface CounterRegistry {
    void registerCounter(Counter<?> counter, long instantPeriod);
}
