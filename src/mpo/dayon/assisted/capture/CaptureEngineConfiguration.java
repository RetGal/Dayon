package mpo.dayon.assisted.capture;

import mpo.dayon.common.capture.Gray8Bits;
import mpo.dayon.common.configuration.Configuration;
import mpo.dayon.common.preference.Preferences;

public class CaptureEngineConfiguration extends Configuration {
	private static final String PREF_VERSION = "assistant.capture.version";

	private static final String PREF_CAPTURE_TICK = "assistant.capture.tick";

	private static final String PREF_CAPTURE_QUANTIZATION = "assistant.capture.grayLevelQuantization";

	/**
	 * A capture is performed every tick (millis).
	 */
	private final int captureTick;

	/**
	 * The actual number of gray levels.
	 */
	private final Gray8Bits captureQuantization;

	/**
	 * Default : takes its values from the current preferences.
	 *
	 * @see mpo.dayon.common.preference.Preferences
	 */
	public CaptureEngineConfiguration() {
		final Preferences prefs = Preferences.getPreferences();

		final int version = prefs.getIntPreference(PREF_VERSION, 0);

		if (!prefs.isNull() && version == 0) {
			captureTick = (int) (1000.0 / prefs.getDoublePreference("generations", 2.0));
			captureQuantization = prefs.getEnumPreference("grayLevels", Gray8Bits.X_256, Gray8Bits.values());

			persist(true);
		} else {
			captureTick = prefs.getIntPreference(PREF_CAPTURE_TICK, 500);
			captureQuantization = prefs.getEnumPreference(PREF_CAPTURE_QUANTIZATION, Gray8Bits.X_256, Gray8Bits.values());
		}
	}

	public CaptureEngineConfiguration(int captureTick, Gray8Bits captureQuantization) {
		this.captureTick = captureTick;
		this.captureQuantization = captureQuantization;
	}

	public int getCaptureTick() {
		return captureTick;
	}

	public Gray8Bits getCaptureQuantization() {
		return captureQuantization;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}

		final CaptureEngineConfiguration that = (CaptureEngineConfiguration) o;

		return captureTick == that.captureTick && captureQuantization == that.captureQuantization;
	}

	@Override
	public int hashCode() {
		int result = captureTick;
		result = 31 * result + (captureQuantization != null ? captureQuantization.hashCode() : 0);
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
			props.set(PREF_CAPTURE_TICK, String.valueOf(captureTick));
			props.set(PREF_CAPTURE_QUANTIZATION, String.valueOf(captureQuantization.ordinal()));

			if (clear) // migration support (!)
			{
				props.clear("generations");
				props.clear("grayLevels");
				props.clear("tiles");
			}
		}

		Preferences.getPreferences().update(props); // atomic (!)
	}

	@Override
	public String toString() {
		return "[tick:" + captureTick + "][quantization:" + captureQuantization + "]";
	}
}
