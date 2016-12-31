package mpo.dayon.assisted.mouse;

import java.awt.Point;

import mpo.dayon.common.event.Listener;

public interface MouseEngineListener extends Listener {
	/**
	 * May block (!)
	 */
	boolean onLocationUpdated(Point location);
}
