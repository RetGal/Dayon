package mpo.dayon.assistant.network;

import mpo.dayon.common.configuration.Configuration;
import mpo.dayon.common.preference.Preferences;

import java.util.Objects;

public class NetworkAssistantEngineConfiguration extends Configuration {
	private static final String PREF_VERSION = "assistant.network.version";

	private static final String PREF_PORT_NUMBER = "assistant.network.portNumber";

	private static final String PREF_TOKEN_SERVER_URL = "assistant.network.tokenServerUrl";

	private static final String PREF_AUTO_ACCEPT = "assistant.network.autoAccept";

	private static final String PREF_ICE_TURN_SERVERS = "ice.turn.servers";

	private final int port;

	private final String tokenServerUrl;

	private final String iceTurnServers;

	private final boolean autoAccept;

	private boolean monochromePeer = false;

	private boolean terminablePeer = true;

	/**
	 * Default : takes its values from the current preferences.
	 */
	public NetworkAssistantEngineConfiguration() {
		final Preferences prefs = Preferences.getPreferences();
		port = prefs.getIntPreference(PREF_PORT_NUMBER, 8080);
		tokenServerUrl = prefs.getStringPreference(PREF_TOKEN_SERVER_URL, DEFAULT_TOKEN_SERVER_URL);
		autoAccept = prefs.getBooleanPreference(PREF_AUTO_ACCEPT, false);
		iceTurnServers = prefs.getStringPreference(PREF_ICE_TURN_SERVERS, "");
	}

	public NetworkAssistantEngineConfiguration(int port, String tokenServerUrl, boolean autoAccept) {
		this.port = port;
		this.tokenServerUrl = tokenServerUrl;
		this.autoAccept = autoAccept;
		iceTurnServers = Preferences.getPreferences().getStringPreference(PREF_ICE_TURN_SERVERS, "");
	}

	public int getPort() {
		return port;
	}

	public String getTokenServerUrl() {
		return tokenServerUrl;
	}

	public String getIceTurnServers() {
		return iceTurnServers;
	}

	public boolean isAutoAccept() {
		return autoAccept;
	}

	public boolean isMonochromePeer() {
		return monochromePeer;
	}

	public void setMonochromePeer(boolean monochromePeer) {
		this.monochromePeer = monochromePeer;
	}

	public void setTerminablePeer(boolean terminable) {
		this.terminablePeer = terminable;
	}

	public boolean isTerminablePeer() {
		return terminablePeer;
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

		return port == that.port && tokenServerUrl.equals(that.tokenServerUrl) && autoAccept == that.autoAccept && iceTurnServers.equals(that.iceTurnServers);
	}

	@Override
	public int hashCode() {
		return Objects.hash(port, tokenServerUrl, autoAccept, iceTurnServers);
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
		props.set(PREF_AUTO_ACCEPT, String.valueOf(autoAccept));
		props.set(PREF_ICE_TURN_SERVERS, iceTurnServers);

		if (clear) // migration support (!)
		{
			props.clear("assistantPortNumber");
		}
		Preferences.getPreferences().update(props); // atomic (!)
	}

}
