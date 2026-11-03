package mpo.dayon.common.compressor;

import mpo.dayon.common.preference.Preferences;
import mpo.dayon.common.squeeze.CompressionMethod;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CompressorEngineConfigurationTest {

    @Test
    void defaultConfigurationTakesTheValuesFromThePreferences() {
        // given
        final Preferences preferences = Preferences.getPreferences();

        // when
        final CompressorEngineConfiguration configuration = new CompressorEngineConfiguration();

        // then
        assertEquals(preferences.getEnumPreference("assistant.compression.method", CompressionMethod.ZIP, CompressionMethod.values()), configuration.getMethod());
        assertEquals(preferences.getBooleanPreference("assistant.compression.useCache", true), configuration.useCache());
        assertEquals(preferences.getIntPreference("assistant.compression.cacheMaxSize", 32 * 1024), configuration.getCacheMaxSize());
        assertEquals(preferences.getIntPreference("assistant.compression.cachePurgeSize", 16 * 1024), configuration.getCachePurgeSize());
    }

    @Test
    void explicitConfigurationHoldsTheValues() {
        // given, when
        final CompressorEngineConfiguration configuration = new CompressorEngineConfiguration(CompressionMethod.XZ, true, 1024, 512);

        // then
        assertEquals(CompressionMethod.XZ, configuration.getMethod());
        assertTrue(configuration.useCache());
        assertEquals(1024, configuration.getCacheMaxSize());
        assertEquals(512, configuration.getCachePurgeSize());
    }

    @Test
    void equalsAndHashCode() {
        // given
        final CompressorEngineConfiguration first = new CompressorEngineConfiguration(CompressionMethod.ZIP, true, 1024, 512);
        final CompressorEngineConfiguration second = new CompressorEngineConfiguration(CompressionMethod.ZIP, true, 1024, 512);

        // when, then
        assertNotNull(first);
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(new CompressorEngineConfiguration(CompressionMethod.XZ, true, 1024, 512), first);
        assertNotEquals(new CompressorEngineConfiguration(CompressionMethod.ZIP, false, 1024, 512), first);
        assertNotEquals(new CompressorEngineConfiguration(CompressionMethod.ZIP, true, 2048, 512), first);
        assertNotEquals(new CompressorEngineConfiguration(CompressionMethod.ZIP, true, 1024, 256), first);
        assertNotEquals(new Object(), first);
    }

    @Test
    void toStringContainsAllValues() {
        // given
        final CompressorEngineConfiguration configuration = new CompressorEngineConfiguration(CompressionMethod.NONE, false, 1024, 512);

        // when, then
        assertEquals("[method:NONE][useCache:false][max:1024][purge:512]", configuration.toString());
    }

    @Test
    void persistAndReload() {
        // given
        final CompressorEngineConfiguration original = new CompressorEngineConfiguration();
        final CompressorEngineConfiguration configuration = new CompressorEngineConfiguration(CompressionMethod.XZ, false, 2048, 1024);

        try {
            // when
            configuration.persist();

            // then
            assertEquals(new CompressorEngineConfiguration(), configuration);
        } finally {
            original.persist();
        }
    }
}
