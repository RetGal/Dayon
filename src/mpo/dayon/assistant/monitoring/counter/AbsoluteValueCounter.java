package mpo.dayon.assistant.monitoring.counter;

public abstract class AbsoluteValueCounter extends Counter<Long> {
	private long instantValue = 0;

	AbsoluteValueCounter(String uid, String shortDescription) {
		super(uid, shortDescription);
	}

	public void add(long value) {
		synchronized (this) {
			instantValue += value;
		}
	}

	public void computeAndResetInstantValue() {
		Long value = null;

		synchronized (this) {
			if (instantStart != -1) {
				value = instantValue;

				instantStart = System.currentTimeMillis();
				instantValue = 0;
			}
		}

		fireOnInstantValueUpdated(value);
	}

}