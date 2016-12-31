package mpo.dayon.assistant.monitoring;

import java.util.Timer;
import java.util.TimerTask;

import mpo.dayon.assistant.monitoring.counter.Counter;

public class BigBrother {
	private final static BigBrother INSTANCE = new BigBrother();

	private final Timer timer = new Timer("BigBrother");

	private BigBrother() {
	}

	public static BigBrother get() {
		return INSTANCE;
	}

	/**
	 * @param instantRatePeriod
	 *            millis
	 */
	public void registerCounter(final Counter counter, final long instantRatePeriod) {
		timer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				counter.computeAndResetInstantValue();
			}
		}, 0, instantRatePeriod);
	}

	public void registerRamInfo(TimerTask callback) {
		timer.scheduleAtFixedRate(callback, 0, 1000);
	}
}
