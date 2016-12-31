package mpo.dayon.assisted.gui;

import mpo.dayon.common.configuration.Configuration;
import mpo.dayon.common.preference.Preferences;

public class AssistedFrameConfiguration extends Configuration {
	private static final String PREF_VERSION = "assisted.frame.version";

	private static final String PREF_X = "assisted.frame.x";

	private static final String PREF_Y = "assisted.frame.y";

	private static final String PREF_WIDTH = "assisted.frame.width";

	private static final String PREF_HEIGHT = "assisted.frame.height";

	private final int x;

	private final int y;

	private final int width;

	private final int height;

	/**
	 * Default : takes its values from the current preferences.
	 *
	 * @see mpo.dayon.common.preference.Preferences
	 */
	public AssistedFrameConfiguration() {
		final Preferences prefs = Preferences.getPreferences();

		final int version = prefs.getIntPreference(PREF_VERSION, 0);

		if (!prefs.isNull() && version == 0) {
			x = prefs.getIntPreference("assistedFrameX", 100);
			y = prefs.getIntPreference("assistedFrameY", 100);
			width = prefs.getIntPreference("assistedFrameWidth", 400);
			height = prefs.getIntPreference("assistedFrameHeight", 200);

			persist(true);
		} else {
			x = prefs.getIntPreference(PREF_X, 100);
			y = prefs.getIntPreference(PREF_Y, 100);
			width = prefs.getIntPreference(PREF_WIDTH, 400);
			height = prefs.getIntPreference(PREF_HEIGHT, 200);
		}
	}

	public AssistedFrameConfiguration(int x, int y, int width, int height) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
	}

	public int getX() {
		return x;
	}

	public int getY() {
		return y;
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}

		final AssistedFrameConfiguration that = (AssistedFrameConfiguration) o;

		return height == that.height && width == that.width && x == that.x && y == that.y;
	}

	@Override
	public int hashCode() {
		int result = x;
		result = 31 * result + y;
		result = 31 * result + width;
		result = 31 * result + height;
		return result;
	}

	/**
	 * @param clear
	 *            allows for clearing properties from previous version
	 */
	protected void persist(boolean clear) {
		final Preferences.Props props = new Preferences.Props();
		{
			props.set(PREF_VERSION, String.valueOf(1));
			props.set(PREF_X, String.valueOf(x));
			props.set(PREF_Y, String.valueOf(y));
			props.set(PREF_WIDTH, String.valueOf(width));
			props.set(PREF_HEIGHT, String.valueOf(height));

			if (clear) // migration support (!)
			{
				props.clear("assistedFrameX");
				props.clear("assistedFrameY");
				props.clear("assistedFrameWidth");
				props.clear("assistedFrameHeight");
			}
		}

		Preferences.getPreferences().update(props); // atomic (!)
	}

}
