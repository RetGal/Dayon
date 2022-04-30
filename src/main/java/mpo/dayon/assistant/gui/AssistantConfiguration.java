package mpo.dayon.assistant.gui;

import mpo.dayon.common.configuration.Configuration;
import mpo.dayon.common.preference.Preferences;
import mpo.dayon.common.utils.SystemUtilities;

import static mpo.dayon.common.preference.Preferences.*;

public class AssistantConfiguration extends Configuration {
	private static final String PREF_VERSION = "assistant.version";

	private static final String PREF_LOOK_AND_FEEL = "assistant.lookAndFeel";

	private final String lookAndFeelClassName;

	/**
	 * Default : takes its values from the current preferences.
	 *
	 * @see mpo.dayon.common.preference.Preferences
	 */
	public AssistantConfiguration() {
		final Preferences prefs = getPreferences();
		lookAndFeelClassName = prefs.getStringPreference(PREF_LOOK_AND_FEEL, SystemUtilities.getDefaultLookAndFeel());
	}

	AssistantConfiguration(String lookAndFeelClassName) {
		this.lookAndFeelClassName = lookAndFeelClassName;
	}

	String getLookAndFeelClassName() {
		return lookAndFeelClassName;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		final AssistantConfiguration that = (AssistantConfiguration) o;
		return lookAndFeelClassName.equals(that.getLookAndFeelClassName());
	}

	@Override
	public int hashCode() {
		return lookAndFeelClassName.hashCode();
	}

	/**
	 * @param clear
	 *            allows for clearing properties from previous version
	 */
	@Override
    protected void persist(boolean clear) {
		final Props props = new Props();
		props.set(PREF_VERSION, String.valueOf(1));
		props.set(PREF_LOOK_AND_FEEL, lookAndFeelClassName);
		getPreferences().update(props); // atomic (!)
	}

}
