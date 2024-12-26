package mpo.dayon.common.monitoring.counter;

import mpo.dayon.common.monitoring.BigBrother;
import mpo.dayon.common.utils.UnitUtilities;

public class BitCounter extends RateCounter {
	public BitCounter(String uid, String shortDescription, BigBrother bigBrother) {
		super(uid, shortDescription, bigBrother);
	}

	@Override
    public String formatRate(Double rate) {
		if (rate == null || Double.isNaN(rate)) {
			return "- bit/s";
		}
		return String.format("%s/s", UnitUtilities.toBitSize(rate));
	}

	@Override
    public int getWidth() {
		return 100;
	}
}
