package mpo.dayon.common.monitoring.counter;

import mpo.dayon.common.event.Listener;

public interface CounterListener<T> extends Listener {
	void onInstantValueUpdated(Counter<?> counter, T value);
}
