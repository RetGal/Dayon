package mpo.dayon.common.monitoring.counter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MergedTileCounterTest {

    @Test
    void counterMetadata() {
        // given, when
        MergedTileCounter counter = new MergedTileCounter("mergedTiles", "Merged tiles");

        // then
        assertEquals("mergedTiles", counter.getUid());
        assertEquals("Merged tiles", counter.getShortDescription());
        assertEquals(40, counter.getWidth());
    }

    @Test
    void formatInstantValuePrintsRawLong() {
        // given
        MergedTileCounter counter = new MergedTileCounter(null, null);

        // when, then
        assertEquals("123", counter.formatInstantValue(123L));
        assertEquals("-", counter.formatInstantValue(null));
    }

    @Test
    void computeAndResetInstantValueFiresTheAccumulatedValue() {
        // given
        MergedTileCounter counter = new MergedTileCounter(null, null);
        counter.start(1000);
        CounterListener<Long> listener = (CounterListener<Long>) mock(CounterListener.class);
        counter.addListener(listener);

        // when
        counter.add(1);
        counter.add(2);
        counter.computeAndResetInstantValue();

        // then
        verify(listener, atLeastOnce()).onInstantValueUpdated(counter, 3L);
    }

    @Test
    void computeAndResetResetsTheAccumulator() {
        // given
        MergedTileCounter counter = new MergedTileCounter(null, null);
        counter.start(1000);
        CounterListener<Long> listener = (CounterListener<Long>) mock(CounterListener.class);
        counter.addListener(listener);
        counter.add(5);
        counter.computeAndResetInstantValue();

        // when
        counter.computeAndResetInstantValue();

        // then
        verify(listener, atLeastOnce()).onInstantValueUpdated(counter, 0L);
    }
}
