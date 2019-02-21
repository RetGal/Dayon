package mpo.dayon.assisted.gui;

import mpo.dayon.common.configuration.Configuration;
import mpo.dayon.common.gui.common.Dimension;
import mpo.dayon.common.gui.common.Position;
import mpo.dayon.common.preference.Preferences;

public class AssistedFrameConfiguration extends Configuration {
	
	private static final String PREF_VERSION = "assisted.frame.version";
	private static final String PREF_X = "assisted.frame.x";
	private static final String PREF_Y = "assisted.frame.y";
	private static final String PREF_WIDTH = "assisted.frame.width";
	private static final String PREF_HEIGHT = "assisted.frame.height";

	private final Position position;
	private final Dimension dimension;

	/**
	 * Default : takes its values from the current preferences.
	 *
	 * @see mpo.dayon.common.preference.Preferences
	 */
	public AssistedFrameConfiguration() {
		final Preferences prefs = Preferences.getPreferences();

		final int version = prefs.getIntPreference(PREF_VERSION, 0);

		if (!prefs.isNull() && version == 0) {
			position = new Position(prefs.getIntPreference("assistedFrameX", 100), prefs.getIntPreference("assistedFrameY", 100));
			dimension = new Dimension(prefs.getIntPreference("assistedFrameWidth", 400), prefs.getIntPreference("assistedFrameHeight", 200));
			persist(true);
		} else {
			position = new Position(prefs.getIntPreference(PREF_X, 100), prefs.getIntPreference(PREF_Y, 100));
			dimension = new Dimension(prefs.getIntPreference(PREF_WIDTH, 400), prefs.getIntPreference(PREF_HEIGHT, 200));
		}
	}

	public AssistedFrameConfiguration(Position position, Dimension dimension) {
		this.position = position;
		this.dimension = dimension;
	}

	public int getX() {
		return position.getX();
	}

	public int getY() {
		return position.getY();
	}

	public int getWidth() {
		return dimension.getWidth();
	}

	public int getHeight() {
		return dimension.getHeight();
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

		return dimension.getHeight() == that.dimension.getHeight() && dimension.getWidth() == that.dimension.getWidth() && position.getX() == that.position.getX() && position.getY() == that.position.getY();
	}

	@Override
	public int hashCode() {
		int result = position.getX();
		result = 31 * result + position.getY();
		result = 31 * result + dimension.getWidth();
		result = 31 * result + dimension.getHeight();
		return result;
	}

	/**
	 * @param clear
	 *            allows for clearing properties from previous version
	 */
	@Override
    protected void persist(boolean clear) {
		final Preferences.Props props = new Preferences.Props(); 
		{
			props.set(PREF_VERSION, String.valueOf(1));
			props.set(PREF_X, String.valueOf(position.getX()));
			props.set(PREF_Y, String.valueOf(position.getY()));
			props.set(PREF_WIDTH, String.valueOf(dimension.getWidth()));
			props.set(PREF_HEIGHT, String.valueOf(dimension.getHeight()));

			// migration support (!)
			if (clear) {
				props.clear("assistedFrameX");
				props.clear("assistedFrameY");
				props.clear("assistedFrameWidth");
				props.clear("assistedFrameHeight");
			}
		}

		Preferences.getPreferences().update(props); // atomic (!)
	}

}
