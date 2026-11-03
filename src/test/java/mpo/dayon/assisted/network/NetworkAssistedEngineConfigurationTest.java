package mpo.dayon.assisted.network;

import mpo.dayon.common.configuration.Configuration;
import mpo.dayon.common.preference.Preferences;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NetworkAssistedEngineConfigurationTest {

    private static final String TOKEN_SERVER_URL = "https://example.com/dayon/";

    @Test
    void defaultConfigurationTakesTheValuesFromThePreferences() {
        // given
        final Preferences preferences = Preferences.getPreferences();

        // when
        final NetworkAssistedEngineConfiguration configuration = new NetworkAssistedEngineConfiguration();

        // then
        assertEquals(preferences.getStringPreference("assisted.network.serverName", "localhost"), configuration.getServerName());
        assertEquals(preferences.getIntPreference("assisted.network.serverPortNumber", 8080), configuration.getServerPort());
        assertEquals(preferences.getBooleanPreference("assisted.network.autoConnect", false), configuration.isAutoConnect());
        assertEquals(preferences.getStringPreference("assisted.network.tokenServerUrl", Configuration.DEFAULT_TOKEN_SERVER_URL), configuration.getTokenServerUrl());
        assertNotNull(configuration.getIceTurnServers());
    }

    @Test
    void explicitConfigurationsHoldTheValues() {
        // when, then
        final NetworkAssistedEngineConfiguration withoutAutoConnect = new NetworkAssistedEngineConfiguration("dayon.example.com", 1234);
        assertEquals("dayon.example.com", withoutAutoConnect.getServerName());
        assertEquals(1234, withoutAutoConnect.getServerPort());
        assertEquals(Preferences.getPreferences().getBooleanPreference("assisted.network.autoConnect", false), withoutAutoConnect.isAutoConnect());

        final NetworkAssistedEngineConfiguration withAutoConnect = new NetworkAssistedEngineConfiguration("dayon.example.com", 1234, true);
        assertTrue(withAutoConnect.isAutoConnect());

        final NetworkAssistedEngineConfiguration withTokenServer = new NetworkAssistedEngineConfiguration("dayon.example.com", 1234, true, TOKEN_SERVER_URL);
        assertEquals(TOKEN_SERVER_URL, withTokenServer.getTokenServerUrl());
    }

    @Test
    void serverNameAndPortCanBeChanged() {
        // given
        final NetworkAssistedEngineConfiguration configuration = new NetworkAssistedEngineConfiguration("dayon.example.com", 1234);

        // when
        configuration.setServerName("other.example.com");
        configuration.setServerPort(4321);

        // then
        assertEquals("other.example.com", configuration.getServerName());
        assertEquals(4321, configuration.getServerPort());
    }

    @Test
    void equalsAndHashCode() {
        // given
        final NetworkAssistedEngineConfiguration first = new NetworkAssistedEngineConfiguration("dayon.example.com", 1234, true, TOKEN_SERVER_URL);
        final NetworkAssistedEngineConfiguration second = new NetworkAssistedEngineConfiguration("dayon.example.com", 1234, true, TOKEN_SERVER_URL);

        // when, then
        assertEquals(first, first);
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, new NetworkAssistedEngineConfiguration("other.example.com", 1234, true, TOKEN_SERVER_URL));
        assertNotEquals(first, new NetworkAssistedEngineConfiguration("dayon.example.com", 4321, true, TOKEN_SERVER_URL));
        assertNotEquals(first, new NetworkAssistedEngineConfiguration("dayon.example.com", 1234, false, TOKEN_SERVER_URL));
        assertNotEquals(first, null);
        assertNotEquals(first, new Object());
    }

    @Test
    void toStringContainsTheServerAndThePort() {
        // given
        final NetworkAssistedEngineConfiguration configuration = new NetworkAssistedEngineConfiguration("dayon.example.com", 1234);

        // when, then
        assertEquals("[ip:dayon.example.com][port:1234]", configuration.toString());
    }

    @Test
    void persistAndReload() {
        // given
        final NetworkAssistedEngineConfiguration original = new NetworkAssistedEngineConfiguration();
        final NetworkAssistedEngineConfiguration configuration = new NetworkAssistedEngineConfiguration("dayon.example.com", 1234, true, TOKEN_SERVER_URL);

        try {
            // when
            configuration.persist();

            // then
            final NetworkAssistedEngineConfiguration reloaded = new NetworkAssistedEngineConfiguration();
            assertEquals("dayon.example.com", reloaded.getServerName());
            assertEquals(1234, reloaded.getServerPort());
            assertTrue(reloaded.isAutoConnect());
            assertEquals(TOKEN_SERVER_URL, reloaded.getTokenServerUrl());
        } finally {
            original.persist();
        }
    }
}
