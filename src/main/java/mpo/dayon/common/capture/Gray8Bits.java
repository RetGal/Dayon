package mpo.dayon.common.capture;

import mpo.dayon.common.babylon.Babylon;

/**
 * =====================================================================================================================
 * The ordinal is persisted within the preferences.
 * =====================================================================================================================
 */
public enum Gray8Bits {

	X_256(256),
	X_128(128),
	X_64(64),
	X_32(32),
	X_16(16),
	X_8(8),
	X_4(4);

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
