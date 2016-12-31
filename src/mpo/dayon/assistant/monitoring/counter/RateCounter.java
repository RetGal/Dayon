package mpo.dayon.assistant.monitoring.counter;

import java.util.LinkedList;

public abstract class RateCounter extends Counter<Double> {
	/**
	 * The last computed N instant rates : most recent is first (head).
	 */
	private final LinkedList<Double> instantRateHistory = new LinkedList<>();

	private double instantValue = 0;

	RateCounter(String uid, String shortDescription) {
		super(uid, shortDescription);
	}

	public String formatInstantValue(Double value) {
		return formatRate(value);
	}

	protected abstract String formatRate(Double rate);

	public void add(double value) {
		synchronized (this) {
			instantValue += value;
		}
	}

	public void computeAndResetInstantValue() {
		double rate = Double.NaN;

		synchronized (this) {
			if (instantStart != -1) {
				final long elapsed = System.currentTimeMillis() - instantStart;
				rate = 1000.0 * instantValue / (double) elapsed;

				synchronized (instantRateHistory) {
					instantRateHistory.addFirst(rate);
					if (instantRateHistory.size() > 10) {
						instantRateHistory.removeLast();
					}
				}

				instantStart = System.currentTimeMillis();
				instantValue = 0;
			}
		}

		fireOnInstantValueUpdated(rate);
	}

	/**
	 * @return most recent is first in the returned array
	 */
	public double[] getInstantRateHistory() {
		synchronized (instantRateHistory) {
			final double[] history = new double[instantRateHistory.size()];

			if (history.length > 0) {
				int idx = 0;

				for (Double rate : instantRateHistory) {
					history[idx++] = rate;
				}
			}

			return history;
		}
	}

}