package mpo.dayon.assistant.monitoring.counter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import mpo.dayon.assistant.monitoring.BigBrother;

public abstract class Counter<T> {
	private final List<CounterListener<T>> listeners = new CopyOnWriteArrayList<>();

	private final String uid;

	private final String shortDescription;

	long instantStart;

	Counter(String uid, String shortDescription) {
		this.uid = uid;
		this.shortDescription = shortDescription;
	}

	public void addListener(CounterListener<T> listener) {
		listeners.add(listener);
	}

	public void removeListener(CounterListener<T> listener) {
		listeners.remove(listener);
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
		synchronized (this) {
			this.instantStart = System.currentTimeMillis();
		}
	}

	/**
	 * Initializes that counter and registers it to the {@link BigBrother}.
	 *
	 * @param instantPeriod millis
	 */
	public void start(long instantPeriod) {
		initialize();
		BigBrother.get().registerCounter(this, instantPeriod);
	}

	public abstract void computeAndResetInstantValue();

	public abstract String formatInstantValue(T value);

	public abstract int getWidth();

	void fireOnInstantValueUpdated(T value) {

		for (final CounterListener<T> xlistener : listeners) {
			xlistener.onInstantValueUpdated(this, value);
		}
	}
}
