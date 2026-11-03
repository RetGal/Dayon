package mpo.dayon.common.monitoring.counter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SkippedTileCounterTest {

    @Test
    void computeAndResetInstantValueFiresTheAccumulatedValue() {
        // given
        SkippedTileCounter counter = new SkippedTileCounter(null, null);
        counter.start(1000);
        CounterListener<Long> listener = (CounterListener<Long>) mock(CounterListener.class);
        counter.addListener(listener);

        // when
        for (int idx = 0; idx < 7; idx++) {
            counter.add(1);
        }
        counter.computeAndResetInstantValue();

        // then
        verify(listener, atLeastOnce()).onInstantValueUpdated(counter, 7L);
    }
}
