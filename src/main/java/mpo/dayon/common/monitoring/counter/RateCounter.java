package mpo.dayon.common.monitoring.counter;

import mpo.dayon.common.monitoring.BigBrother;

public abstract class RateCounter extends Counter<Double> {

	private double instantValue = 0;

	RateCounter(String uid, String shortDescription, BigBrother bigBrother) {
		super(uid, shortDescription, bigBrother);
	}

	@Override
    public String formatInstantValue(Double value) {
		return formatRate(value);
	}

	protected abstract String formatRate(Double rate);

	public synchronized void add(double value) {
        instantValue += value;
    }

	@Override
    public void computeAndResetInstantValue() {
		double rate = Double.NaN;

		synchronized (this) {
			if (instantStart.get() != -1) {
				final long elapsed = System.currentTimeMillis() - instantStart.get();
				rate = 1000.0 * instantValue / elapsed;
				instantStart.set(System.currentTimeMillis());
				instantValue = 0;
			}
		}

		fireOnInstantValueUpdated(rate);
	}

}