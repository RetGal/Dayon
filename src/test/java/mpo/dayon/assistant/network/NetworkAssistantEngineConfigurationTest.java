package mpo.dayon.assistant.network;

import mpo.dayon.common.configuration.Configuration;
import mpo.dayon.common.preference.Preferences;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NetworkAssistantEngineConfigurationTest {

    private static final String TOKEN_SERVER_URL = "https://example.com/dayon/";

    @Test
    void defaultConfigurationTakesTheValuesFromThePreferences() {
        // given
        final Preferences preferences = Preferences.getPreferences();

        // when
        final NetworkAssistantEngineConfiguration configuration = new NetworkAssistantEngineConfiguration();

        // then
        assertEquals(preferences.getIntPreference("assistant.network.portNumber", 8080), configuration.getPort());
        assertEquals(preferences.getStringPreference("assistant.network.tokenServerUrl", Configuration.DEFAULT_TOKEN_SERVER_URL), configuration.getTokenServerUrl());
        assertEquals(preferences.getBooleanPreference("assistant.network.autoAccept", false), configuration.isAutoAccept());
        assertNotNull(configuration.getIceTurnServers());
    }

    @Test
    void explicitConfigurationHoldsTheValues() {
        // given, when
        final NetworkAssistantEngineConfiguration configuration = new NetworkAssistantEngineConfiguration(9090, TOKEN_SERVER_URL, true);

        // then
        assertEquals(9090, configuration.getPort());
        assertEquals(TOKEN_SERVER_URL, configuration.getTokenServerUrl());
        assertTrue(configuration.isAutoAccept());
        assertFalse(configuration.isMonochromePeer());
        assertTrue(configuration.isTerminablePeer());
    }

    @Test
    void peerFlagsCanBeChanged() {
        // given
        final NetworkAssistantEngineConfiguration configuration = new NetworkAssistantEngineConfiguration(9090, TOKEN_SERVER_URL, false);

        // when
        configuration.setMonochromePeer(true);
        configuration.setTerminablePeer(false);

        // then
        assertTrue(configuration.isMonochromePeer());
        assertFalse(configuration.isTerminablePeer());
        assertEquals(configuration, new NetworkAssistantEngineConfiguration(9090, TOKEN_SERVER_URL, false), "the peer flags are not part of the identity");
    }

    @Test
    void equalsAndHashCode() {
        // given
        final NetworkAssistantEngineConfiguration first = new NetworkAssistantEngineConfiguration(9090, TOKEN_SERVER_URL, true);
        final NetworkAssistantEngineConfiguration second = new NetworkAssistantEngineConfiguration(9090, TOKEN_SERVER_URL, true);

        // when, then
        assertNotNull(first);
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(new NetworkAssistantEngineConfiguration(9091, TOKEN_SERVER_URL, true), first);
        assertNotEquals(new NetworkAssistantEngineConfiguration(9090, "https://other.example.com", true), first);
        assertNotEquals(new NetworkAssistantEngineConfiguration(9090, TOKEN_SERVER_URL, false), first);
        assertNotEquals(new Object(), first);
    }

    @Test
    void persistAndReload() {
        // given
        final NetworkAssistantEngineConfiguration original = new NetworkAssistantEngineConfiguration();
        final NetworkAssistantEngineConfiguration configuration = new NetworkAssistantEngineConfiguration(9090, TOKEN_SERVER_URL, true);

        try {
            // when
            configuration.persist();

            // then
            final NetworkAssistantEngineConfiguration reloaded = new NetworkAssistantEngineConfiguration();
            assertEquals(9090, reloaded.getPort());
            assertEquals(TOKEN_SERVER_URL, reloaded.getTokenServerUrl());
            assertTrue(reloaded.isAutoAccept());
        } finally {
            original.persist();
        }
    }
}
