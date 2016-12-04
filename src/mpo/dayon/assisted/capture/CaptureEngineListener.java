package mpo.dayon.assisted.capture;

import mpo.dayon.common.capture.Capture;
import mpo.dayon.common.event.Listener;

public interface CaptureEngineListener extends Listener
{
    /**
     * Must not block.
     */
    void onCaptured(Capture capture);

    /**
     * Must not block: debugging purpose.
     */
    void onRawCaptured(int id, byte[] grays);
}
