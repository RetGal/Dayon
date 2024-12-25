package mpo.dayon.assistant.network;

import mpo.dayon.common.configuration.Configuration;
import mpo.dayon.common.preference.Preferences;

import java.util.Objects;

public class NetworkAssistantEngineConfiguration extends Configuration {
	private static final String PREF_VERSION = "assistant.network.version";

	private static final String PREF_PORT_NUMBER = "assistant.network.portNumber";

	private static final String PREF_TOKEN_SERVER_URL = "assistant.network.tokenServerUrl";

	private final int port;

	private final String tokenServerUrl;

	private boolean monochromePeer = false;

	/**
	 * Default : takes its values from the current preferences.
	 */
	public NetworkAssistantEngineConfiguration() {
		port = Preferences.getPreferences().getIntPreference(PREF_PORT_NUMBER, 8080);
		tokenServerUrl = Preferences.getPreferences().getStringPreference(PREF_TOKEN_SERVER_URL, DEFAULT_TOKEN_SERVER_URL);
	}

	public NetworkAssistantEngineConfiguration(int port, String tokenServerUrl) {
		this.port = port;
		this.tokenServerUrl = tokenServerUrl;
	}

	public int getPort() {
		return port;
	}

	public String getTokenServerUrl() {
		return tokenServerUrl;
	}

	public boolean isMonochromePeer() {
		return monochromePeer;
	}

	public void setMonochromePeer(boolean monochromePeer) {
		this.monochromePeer = monochromePeer;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}

		final NetworkAssistantEngineConfiguration that = (NetworkAssistantEngineConfiguration) o;

		return port == that.port && tokenServerUrl.equals(that.tokenServerUrl);
	}

	@Override
	public int hashCode() {
		return Objects.hash(port, tokenServerUrl);
	}

	/**
	 * @param clear
	 *            allows for clearing properties from previous version
	 */
	@Override
    protected void persist(boolean clear) {
		final Preferences.Props props = new Preferences.Props();
		props.set(PREF_VERSION, String.valueOf(1));
		props.set(PREF_PORT_NUMBER, String.valueOf(port));
		props.set(PREF_TOKEN_SERVER_URL, tokenServerUrl);

		if (clear) // migration support (!)
		{
			props.clear("assistantPortNumber");
		}
		Preferences.getPreferences().update(props); // atomic (!)
	}

}
