package mpo.dayon.common.monitoring.counter;

import mpo.dayon.common.monitoring.BigBrother;

public class SkippedTileCounter extends AbsoluteValueCounter {
	public SkippedTileCounter(String uid, String shortDescription, BigBrother bigBrother) {
		super(uid, shortDescription, bigBrother);
	}
}