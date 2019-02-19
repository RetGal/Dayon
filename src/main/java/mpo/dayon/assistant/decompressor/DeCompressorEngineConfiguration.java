package mpo.dayon.assistant.decompressor;

import mpo.dayon.common.configuration.Configuration;

public class DeCompressorEngineConfiguration extends Configuration {
	/**
	 * Default : takes its values from the current preferences.
	 *
	 * @see mpo.dayon.common.preference.Preferences
	 */
	public DeCompressorEngineConfiguration() {
	}

	/**
	 * @param clear
	 *            allows for clearing properties from previous version
	 */
	@Override
	protected void persist(boolean clear) {
	}
}
