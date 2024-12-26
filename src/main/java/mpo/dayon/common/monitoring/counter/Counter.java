package mpo.dayon.common.monitoring.counter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import mpo.dayon.common.monitoring.BigBrother;
import mpo.dayon.common.monitoring.CounterRegistry;

public abstract class Counter<T> {
	private final List<CounterListener<T>> listeners = new CopyOnWriteArrayList<>();

	private final String uid;

	private final String shortDescription;

	private final CounterRegistry counterRegistry;

	AtomicLong instantStart;

	Counter(String uid, String shortDescription, CounterRegistry counterRegistry) {
		this.uid = uid;
		this.shortDescription = shortDescription;
		this.counterRegistry = counterRegistry;
	}

	public void addListener(CounterListener<T> listener) {
		listeners.add(listener);
	}

	public String getUid() {
		return uid;
	}

	public String getShortDescription() {
		return shortDescription;
	}

	/**
	 * Setup the starting time of this counter.
	 *
	 * @see #start(long)
	 */
	private void initialize() {
		this.instantStart = new AtomicLong(System.currentTimeMillis());
	}

	/**
	 * Initializes that counter and registers it to the {@link BigBrother}.
	 *
	 * @param instantPeriod millis
	 */
	public void start(long instantPeriod) {
		initialize();
		counterRegistry.registerCounter(this, instantPeriod);
	}

	public abstract void computeAndResetInstantValue();

	public abstract String formatInstantValue(T value);

	public abstract int getWidth();

	void fireOnInstantValueUpdated(T value) {

		for (final CounterListener<T> xListener : listeners) {
			xListener.onInstantValueUpdated(this, value);
		}
	}
}
