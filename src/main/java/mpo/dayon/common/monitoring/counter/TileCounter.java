package mpo.dayon.assistant.monitoring.counter;

public class TileCounter extends AbsoluteValueCounter {
	public TileCounter(String uid, String shortDescription) {
		super(uid, shortDescription);
	}

	public void add(int tiles, int hits) {
		final long value = ((long) tiles << 32) | hits;
		super.add(value);
	}

	@Override
	public String formatInstantValue(Long value) {
		if (value == null) {
			return "- (-)";
		}

		final int tiles = (int) (value >> 32 & 0xffffffffL);
		final int hits = (int) (value & 0xffffffffL);

		final double percent = 100.0 * (hits / (double) tiles);

		if (Double.isNaN(percent)) {
			return String.format("%d (-%%)", tiles);
		}

		return String.format("%d (%.1f%%)", tiles, percent);
	}

	@Override
	public int getWidth() {
		return 100;
	}
}