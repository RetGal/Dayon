package mpo.dayon.common.event;

import static org.junit.jupiter.api.Assertions.*;

import mpo.dayon.assisted.capture.CaptureEngineListener;
import mpo.dayon.common.compressor.CompressorEngine;
import org.junit.jupiter.api.Test;

class ListenersTest {

    private final Listeners<CaptureEngineListener> listeners = new Listeners<>();

    @Test
    void shouldAddListener() {
        // given
        final CompressorEngine compressorEngine = new CompressorEngine();
        assertTrue(listeners.getListeners().isEmpty());
        // when
        listeners.add(compressorEngine);
        // then
        assertEquals(1, listeners.getListeners().size());
        assertEquals(compressorEngine, listeners.getListeners().get(0));
    }

    @Test
    void shouldNotAddNullListener() {
        // given
        assertTrue(listeners.getListeners().isEmpty());
        // when
        listeners.add(null);
        // then
        assertTrue(listeners.getListeners().isEmpty());
    }

    @Test
    void shouldNotFailOnRemoveNullListener() {
        // given
        assertTrue(listeners.getListeners().isEmpty());
        // when
        listeners.remove(null);
        // then
        assertTrue(listeners.getListeners().isEmpty());
    }

}
