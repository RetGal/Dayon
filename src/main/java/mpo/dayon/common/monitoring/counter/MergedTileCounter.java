package mpo.dayon.common.monitoring.counter;

import mpo.dayon.common.monitoring.BigBrother;

public class MergedTileCounter extends AbsoluteValueCounter {
	public MergedTileCounter(String uid, String shortDescription, BigBrother bigBrother) {
		super(uid, shortDescription, bigBrother);
	}
}