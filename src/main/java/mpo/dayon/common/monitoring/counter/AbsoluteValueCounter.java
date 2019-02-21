package mpo.dayon.common.monitoring.counter;

import java.util.concurrent.atomic.AtomicLong;

public abstract class AbsoluteValueCounter extends Counter<Long> {
	private final AtomicLong instantValue = new AtomicLong(0);

	AbsoluteValueCounter(String uid, String shortDescription) {
		super(uid, shortDescription);
	}

	public void add(long value) {
		instantValue.getAndAdd(value);
	}

	@Override
    public void computeAndResetInstantValue() {
		Long value = null;
		
		if (instantStart.get() != -1) {
			value = instantValue.getAndSet(0L);
			instantStart.set(System.currentTimeMillis());
		}

		fireOnInstantValueUpdated(value);
	}
	
	@Override
    public String formatInstantValue(Long value) {
		if (value == null) {
			return "-";
		}

		return String.format("%d", value);
	}

	@Override
    public int getWidth() {
		return 40;
	}

}