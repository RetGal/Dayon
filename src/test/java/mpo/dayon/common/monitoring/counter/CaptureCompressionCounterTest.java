package mpo.dayon.common.monitoring.counter;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CaptureCompressionCounterTest {

    @Test
    void computeAndResetInstantValue() {
        // given
        CaptureCompressionCounter ccc = new CaptureCompressionCounter(null, null);
        ccc.start(1000);
        CounterListener<Double> cl = (CounterListener<Double>) mock(CounterListener.class);
        ccc.addListener(cl);
        ccc.add(1, 10);
        ccc.add(1, 100);

        // when
        ccc.computeAndResetInstantValue();

        // then
        verify(cl).onInstantValueUpdated(ccc, 55.0);
    }
}