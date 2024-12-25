package mpo.dayon.common.configuration;

public abstract class Configuration {
	public static final String DEFAULT_TOKEN_SERVER_URL = "https://fensterkitt.ch/dayon/";

	public final String getDefaultTokenServerUrl() {
		return DEFAULT_TOKEN_SERVER_URL;
	}

	public final void persist() {
		persist(false);
	}

	/**
	 * @param clear
	 *            allows for clearing properties from previous version
	 */
	protected abstract void persist(boolean clear);

}
