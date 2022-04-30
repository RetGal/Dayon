package mpo.dayon.assisted.network;

import mpo.dayon.common.configuration.Configuration;
import mpo.dayon.common.preference.Preferences;

public class NetworkAssistedEngineConfiguration extends Configuration {
    private static final String PREF_VERSION = "assisted.network.version";

    private static final String PREF_SERVER_NAME = "assisted.network.serverName";

    private static final String PREF_SERVER_PORT_NUMBER = "assisted.network.serverPortNumber";

    private final String serverName;

    private final int serverPort;

    /**
     * Default : takes its values from the current preferences.
     */
    public NetworkAssistedEngineConfiguration() {
        final Preferences prefs = Preferences.getPreferences();
        serverName = prefs.getStringPreference(PREF_SERVER_NAME, "localhost");
        serverPort = prefs.getIntPreference(PREF_SERVER_PORT_NUMBER, 8080);
    }

    public NetworkAssistedEngineConfiguration(String serverName, int serverPort) {
        this.serverName = serverName;
        this.serverPort = serverPort;
    }

    public String getServerName() {
        return serverName;
    }

    public int getServerPort() {
        return serverPort;
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
        return serverPort == that.getServerPort() && serverName.equals(that.getServerName());
    }

    @Override
    public int hashCode() {
        return 31 * serverName.hashCode() + serverPort;
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
}
