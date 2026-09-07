package mpo.dayon.assisted.capture;

import mpo.dayon.common.capture.CaptureEngineConfiguration;
import mpo.dayon.common.capture.Gray8Bits;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class CaptureEngineTest {

    @Test
    void testAddListenerTriggersFullCaptureResetWhenConfigured() throws Exception {
        // given
        CaptureEngine engine = new CaptureEngine(new CaptureFactory() {
            @Override
            public Dimension getDimension() {
                return new Dimension(32, 32);
            }

            @Override
            public byte[] captureScreen(Gray8Bits quantization) {
                return new byte[32 * 32];
            }
        });

        // when
        engine.configure(new CaptureEngineConfiguration(200, Gray8Bits.X_128, false));
        engine.addListener(mock(CaptureEngineListener.class));
        Field reconfiguredField = CaptureEngine.class.getDeclaredField("reconfigured");
        reconfiguredField.setAccessible(true);

        // then
        assertTrue(((AtomicBoolean) reconfiguredField.get(engine)).get());
    }
}
