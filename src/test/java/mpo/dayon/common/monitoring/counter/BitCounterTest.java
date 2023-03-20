package mpo.dayon.common.monitoring.counter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BitCounterTest {

    @Test
    void formatRate() {
        // given
        BitCounter bc = new BitCounter(null, null);

        // when, // then
        assertEquals("96.00 Kbit/s", bc.formatRate(96000.0));
    }

    @Test
    void computeAndResetInstantValue() {
        // given
        BitCounter bc = new BitCounter(null, null);
        CounterListener cl = mock(CounterListener.class);
        bc.addListener(cl);
        bc.start(1);

        // when
        bc.computeAndResetInstantValue();

        // then
        verify(cl, timeout(100).atLeastOnce()).onInstantValueUpdated(bc, 0.0d);
    }
}