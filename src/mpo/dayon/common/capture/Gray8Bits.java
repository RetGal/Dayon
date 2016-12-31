package mpo.dayon.common.capture;

import mpo.dayon.common.babylon.Babylon;

/**
 * =====================================================================================================================
 * The ordinal is persisted within the preferences.
 * =====================================================================================================================
 */
public enum Gray8Bits {
	/**
	 * I would say far enough for our initial need.
	 */
	X_256(256),

	/**
	 * Actually quite good for our need.
	 */
	X_32(32),

	/**
	 * A bit more of compression.
	 */
	X_16(16),

	/**
	 * Still very much visible.
	 */
	X_8(8);

	private final int levels;

	Gray8Bits(int levels) {
		this.levels = levels;
	}

	public int getLevels() {
		return levels;
	}

	/**
	 * Currently used by the combo-box (no dedicated model).
	 */
	@Override
	public String toString() {
		return Babylon.translateEnum(this);
	}
}
