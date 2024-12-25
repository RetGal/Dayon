package mpo.dayon.assisted.network;

import mpo.dayon.common.configuration.Configuration;
import mpo.dayon.common.preference.Preferences;

import java.util.Objects;

public class NetworkAssistedEngineConfiguration extends Configuration {
    private static final String PREF_VERSION = "assisted.network.version";

    private static final String PREF_SERVER_NAME = "assisted.network.serverName";

    private static final String PREF_SERVER_PORT_NUMBER = "assisted.network.serverPortNumber";

    private static final String PREF_AUTO_CONNECT = "assisted.network.autoConnect";

    private static final String PREF_TOKEN_SERVER_URL = "assisted.network.tokenServerUrl";

    private final String serverName;

    private final int serverPort;

    private final String tokenServerUrl;

    private final boolean autoConnect;

    /**
     * Default : takes its values from the current preferences.
     */
    public NetworkAssistedEngineConfiguration() {
        final Preferences prefs = Preferences.getPreferences();
        serverName = prefs.getStringPreference(PREF_SERVER_NAME, "localhost");
        serverPort = prefs.getIntPreference(PREF_SERVER_PORT_NUMBER, 8080);
        autoConnect = prefs.getBooleanPreference(PREF_AUTO_CONNECT, false);
        tokenServerUrl = prefs.getStringPreference(PREF_TOKEN_SERVER_URL, DEFAULT_TOKEN_SERVER_URL);
    }

    public NetworkAssistedEngineConfiguration(String serverName, int serverPort) {
        final Preferences prefs = Preferences.getPreferences();
        this.serverName = serverName;
        this.serverPort = serverPort;
        this.autoConnect = prefs.getBooleanPreference(PREF_AUTO_CONNECT, false);
        this.tokenServerUrl = prefs.getStringPreference(PREF_TOKEN_SERVER_URL, DEFAULT_TOKEN_SERVER_URL);
    }

    public NetworkAssistedEngineConfiguration(String serverName, int serverPort, boolean autoConnect) {
        this.serverName = serverName;
        this.serverPort = serverPort;
        this.autoConnect = autoConnect;
        this.tokenServerUrl = Preferences.getPreferences().getStringPreference(PREF_TOKEN_SERVER_URL, DEFAULT_TOKEN_SERVER_URL);
    }

    public NetworkAssistedEngineConfiguration(String serverName, int serverPort, boolean autoConnect, String tokenServerUrl) {
        this.serverName = serverName;
        this.serverPort = serverPort;
        this.autoConnect = autoConnect;
        this.tokenServerUrl = tokenServerUrl;
    }


    public String getServerName() {
        return serverName;
    }

    public int getServerPort() {
        return serverPort;
    }

    public String getTokenServerUrl() {
        return tokenServerUrl;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final NetworkAssistedEngineConfiguration that = (NetworkAssistedEngineConfiguration) o;
        return serverPort == that.getServerPort() && serverName.equals(that.getServerName()) && autoConnect == that.isAutoConnect() && tokenServerUrl.equals(that.getTokenServerUrl());
    }

    @Override
    public int hashCode() {
        return Objects.hash(serverName, serverPort, autoConnect, tokenServerUrl);
    }


    /**
     * @param clear allows for clearing properties from previous version
     */
    @Override
    protected void persist(boolean clear) {
        final Preferences.Props props = new Preferences.Props();
        props.set(PREF_VERSION, String.valueOf(1));
        props.set(PREF_SERVER_NAME, String.valueOf(serverName));
        props.set(PREF_SERVER_PORT_NUMBER, String.valueOf(serverPort));
        props.set(PREF_AUTO_CONNECT, String.valueOf(autoConnect));
        props.set(PREF_TOKEN_SERVER_URL, tokenServerUrl);

        if (clear) // migration support (!)
        {
            props.clear("assistantIpAddress");
            props.clear("assistantPortNumber");
        }
        Preferences.getPreferences().update(props); // atomic (!)
    }

    @Override
    public String toString() {
        return "[ip:" + serverName + "][port:" + serverPort + "]";
    }

    public boolean isAutoConnect() {
        return autoConnect;
    }
}
