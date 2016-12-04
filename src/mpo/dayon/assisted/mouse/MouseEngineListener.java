package mpo.dayon.assisted.mouse;

import mpo.dayon.common.event.Listener;

import java.awt.*;

public interface MouseEngineListener extends Listener
{
    /**
     * May block (!)
     */
    boolean onLocationUpdated(Point location);
}
