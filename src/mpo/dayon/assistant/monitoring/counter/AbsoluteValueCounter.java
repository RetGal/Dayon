package mpo.dayon.assistant.monitoring.counter;

import java.util.concurrent.atomic.AtomicLong;

public abstract class AbsoluteValueCounter extends Counter<Long> {
	private AtomicLong instantValue = new AtomicLong(0);

	AbsoluteValueCounter(String uid, String shortDescription) {
		super(uid, shortDescription);
	}

	public void add(long value) {
		instantValue.getAndAdd(value);
	}

	public void computeAndResetInstantValue() {
		Long value = null;
		
		if (instantStart.get() != -1) {
			value = instantValue.getAndSet(0l);
			instantStart.set(System.currentTimeMillis());
		}

		fireOnInstantValueUpdated(value);
	}
	
	public String formatInstantValue(Long value) {
		if (value == null) {
			return "-";
		}

		return String.format("%d", value);
	}

	public int getWidth() {
		return 40;
	}

}