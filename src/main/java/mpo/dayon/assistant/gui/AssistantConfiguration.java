package mpo.dayon.assistant.gui;

import mpo.dayon.common.configuration.Configuration;

import java.util.Locale;

import static mpo.dayon.common.preference.Preferences.*;

public class AssistantConfiguration extends Configuration {
	private static final String PREF_VERSION = "assistant.version";

	private static final String PREF_LOOK_AND_FEEL = "assistant.lookAndFeel";

	private static final String PREF_LANGUAGE = "assistant.language";

	private final String language;

	/**
	 * Default : takes its values from the current preferences.
	 *
	 * @see mpo.dayon.common.preference.Preferences
	 */
	public AssistantConfiguration() {
		language = getPreferences().getStringPreference(PREF_LANGUAGE, Locale.getDefault().getLanguage());
	}

	AssistantConfiguration(String language) {
		this.language = language;
	}

	String getLanguage() {
		return language;
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
		return language.equals(that.language);
	}

	@Override
	public int hashCode() {
		return language.hashCode();
	}

	/**
	 * @param clear
	 *            allows for clearing properties from previous version
	 */
	@Override
    protected void persist(boolean clear) {
		final Props props = new Props();
		props.set(PREF_VERSION, String.valueOf(1));
		props.set(PREF_LANGUAGE, language);
		props.clear(PREF_LOOK_AND_FEEL);
		getPreferences().update(props); // atomic (!)
	}

}
