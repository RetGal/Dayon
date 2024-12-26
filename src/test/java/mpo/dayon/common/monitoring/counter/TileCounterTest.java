package mpo.dayon.common.monitoring.counter;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TileCounterTest {

    @Test
    void formatInstantValue() {
        // given
        Locale defaultLocale = Locale.getDefault();
        int tiles = 300;
        int hits = 200;
        long sixtySix = ((long) tiles << 32) | hits;
        TileCounter tc= new TileCounter(null, null);

        // when, then
        Locale.setDefault(Locale.forLanguageTag("de-CH"));
        assertEquals("300 (66.7%)", tc.formatInstantValue(sixtySix));

        // when, then
        Locale.setDefault(Locale.forLanguageTag("fr-FR"));
        assertEquals("300 (66,7%)", tc.formatInstantValue(sixtySix));

        Locale.setDefault(defaultLocale);
    }

    @Test
    void computeAndResetInstantValue() {
        // given
        TileCounter tc= new TileCounter(null, null);
        tc.start(1000);
        CounterListener<Long> cl = (CounterListener<Long>) mock(CounterListener.class);
        tc.addListener(cl);
        int tiles = 10;
        int hits = 5;
        tc.add(tiles, hits);
        long fifty = ((long) tiles << 32) | hits;

        // when
        tc.computeAndResetInstantValue();

        // then
        verify(cl).onInstantValueUpdated(tc, fifty);
    }
}