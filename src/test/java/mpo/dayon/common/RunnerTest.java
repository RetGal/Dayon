package mpo.dayon.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static mpo.dayon.common.Runner.*;
import static mpo.dayon.common.utils.SystemUtilities.getJarDir;
import static org.junit.jupiter.api.Assertions.*;

class RunnerTest {

    private static Locale defaultLocale;

    @BeforeAll
    static void getLocale() {
        defaultLocale = Locale.getDefault();
    }

    @AfterEach
    void resetLocale() {
        Locale.setDefault(defaultLocale);
    }

    @Test
    void shouldSetExpectedLocale() {
        // given
        String lang = "fr";
        if (defaultLocale.toLanguageTag().equals(lang)) {
            lang = "ru";
        }
        // when
        String newLanguage = overrideLocale(lang);
        // then
        assertEquals(lang, newLanguage, "Locale should have been altered");
        assertEquals(lang, Locale.getDefault().toLanguageTag(), "Locale should have been altered");
    }

    @Test
    void shouldNotSetUnsupportedLanguage() {
        // given
        String lang = "cn";
        if (defaultLocale.toLanguageTag().equals(lang)) {
            lang = "ru";
        }
        // when
        String newLanguage = overrideLocale(lang);
        // then
        assertNull(newLanguage, "Locale shouldn't have been altered");
        assertEquals(defaultLocale.toLanguageTag(), Locale.getDefault().toLanguageTag(), "Locale shouldn't have been altered");
    }

    @Test
    void shouldIgnoreNull() {
        // given
        // when
        String newLanguage = overrideLocale(null);
        // then
        assertNull(newLanguage, "Locale shouldn't have been altered");
        assertEquals(defaultLocale.toLanguageTag(), Locale.getDefault().toLanguageTag(), "Locale shouldn't have been altered");
    }

    @Test
    void shouldExtractProgramArgs() {
        // given
        String[] args = {"--lang=en,", "-foo=BAR", "bar=gain", "spam"};
        // when
        Map<String, String> programArgs = extractProgramArgs(args);
        // then
        assertEquals(3, programArgs.size(), "Unexpected number of extracted ProgramArgs");
        assertEquals("en", programArgs.get("lang"), "Key 'lang' should have value 'en'");
        assertEquals("BAR", programArgs.get("foo"), "Key 'foo' should have value 'BAR'");
        assertEquals("gain", programArgs.get("bar"), "Key 'bar' should have value 'gain'");
    }

    @ParameterizedTest
    @ValueSource(strings = {"debug", "-debug", "--debug"})
    void shouldSetDebug(String debugArg) {
        // given
        String[] args = {debugArg};
        System.clearProperty("dayon.debug");
        // when
        setDebug(args);
        // then
        assertTrue(Boolean.getBoolean("dayon.debug"), "Debug should have been activated");
    }

    @Test
    void shouldGetOrCreateAppHomeDir() {
        // when
        final File appHomeDir = getOrCreateAppHomeDir();
        // then
        assertNotNull(appHomeDir);
        assertTrue(appHomeDir.getName().endsWith(".dayon"), "No AppHomeDir found");
    }

    @Test
    void readPresetFile() throws IOException {
        // given
        List<String> lines = Arrays.asList("host: \"localhost\"", "port: 8888", "tokenServerUrl: 'https://foo.bar'");
        final Path path = Paths.get(getJarDir(), "test.yaml");
        Files.write(path, lines);
        // when
        final Map<String, String> content = Runner.readPresetFile(path.toString());
        // then
        assertEquals("localhost", content.get("host"));
        assertEquals("8888", content.get("port"));
        assertEquals("https://foo.bar", content.get("tokenServerUrl"));
        assertTrue(isAutoConnect(content));
    }

    @Test
    void isReadableFileShouldReturnFalseForInexistingFile() {
        // given when then
        assertFalse(Runner.isReadableFile(new File("snafu")));
    }

    @Test
    void isReadableFileShouldReturnFalseForDirectory() {
        // given when then
        assertFalse(Runner.isReadableFile(new File(System.getProperty("java.io.tmpdir"))));
    }

    @Test
    void getCustomTokenServerUrlsShouldReturnNullWhenEmpty() {
        // given when then
        assertNull(Runner.getCustomTokenServerUrls(Collections.emptyMap()));
    }

    @Test
    void parseValuesShouldCorrectlyParseListValues() {
        // given
        String[] token = { "backupTokenServerUrls", "[https://www.example.com/rvs/, https://six.seven/rvs/]" };
        // when then
        assertEquals("https://www.example.com/rvs/,https://six.seven/rvs/", Runner.parseValue(token));
    }

    @Test
    void getCustomTokenServerUrlsShouldReturnUrl() {
        // given
        Map<String, String> config = new HashMap<>();
        config.put("tokenServerUrl", "https://foo.bar/rvs/");
        // when then
        assertEquals("https://foo.bar/rvs/", Runner.getCustomTokenServerUrls(config));
    }

    @Test
    void getCustomTokenServerUrlsShouldReturnBackupUrls() {
        // given
        Map<String, String> config = new HashMap<>();
        config.put("backupTokenServerUrls", "https://six.seven/rvs/,https://seven.eight/rvs/");
        // when then
        assertEquals("https://six.seven/rvs/,https://seven.eight/rvs/", Runner.getCustomTokenServerUrls(config));
    }

    @Test
    void getCustomTokenServerUrlsShouldReturnCombinedUrlsInCorrectOrder() {
        // given
        Map<String, String> config = new HashMap<>();
        config.put("tokenServerUrl", "https://foo.bar/rvs/");
        config.put("backupTokenServerUrls", "https://six.seven/rvs/");
        // when then
        assertEquals("https://foo.bar/rvs/,https://six.seven/rvs/", Runner.getCustomTokenServerUrls(config));
    }

    @Test
    void getCustomTokenServerUrlsShouldRemoveDuplicates() {
        // given
        Map<String, String> config = new HashMap<>();
        config.put("tokenServerUrl", "https://foo.bar/rvs/");
        config.put("backupTokenServerUrls", "https://six.seven/rvs/,https://seven.eight/rvs/,https://foo.bar/rvs/");
        // when then
        assertEquals("https://foo.bar/rvs/,https://six.seven/rvs/,https://seven.eight/rvs/",
                Runner.getCustomTokenServerUrls(config));
    }

    @Test
    void detectsTheAssistantMode() {
        // when, then
        assertTrue(hasAssistant(new String[] { "assistant" }));
        assertTrue(hasAssistant(new String[] { "-lang=en", "ASSISTANT" }));
        assertFalse(hasAssistant(new String[] { "-lang=en" }));
        assertFalse(hasAssistant(new String[0]));
    }

    @Test
    void detectsAutoConnect() {
        // when, then
        assertTrue(isAutoConnect(Collections.emptyMap()));
        assertTrue(isAutoConnect(Collections.singletonMap("autoConnect", "true")));
        assertFalse(isAutoConnect(Collections.singletonMap("autoConnect", "false")));
        assertFalse(isAutoConnect(Collections.singletonMap("autoConnect", "FALSE")));
    }

    @Test
    void getOrCreateAppHomeDirReturnsNullWithoutAHomeDirectory() {
        // given
        final String userHome = System.getProperty("user.home");
        System.clearProperty("user.home");

        try {
            // when, then
            assertNull(getOrCreateAppHomeDir());
        } finally {
            System.setProperty("user.home", userHome);
        }
    }

    @Test
    void logAppInfoSetsTheApplicationName() {
        // given
        final String applicationName = System.getProperty("dayon.application.name");

        try {
            // when
            logAppInfo("dayon_test");

            // then
            assertEquals("dayon_test", System.getProperty("dayon.application.name"));
        } finally {
            if (applicationName == null) {
                System.clearProperty("dayon.application.name");
            } else {
                System.setProperty("dayon.application.name", applicationName);
            }
        }
    }

    @Test
    void prepareKeystoreIsSkippedWithoutAnApplicationDirectory() {
        // when, then - must not throw
        assertDoesNotThrow(() -> prepareKeystore(null));
    }

    @Test
    void prepareKeystoreIsSkippedWhenOneExists(@TempDir Path tempDir) throws IOException {
        // given
        final Path keystore = tempDir.resolve("keystore.jks");
        Files.writeString(keystore, "existing");

        // when, then - must not start a new keytool process
        assertDoesNotThrow(() -> prepareKeystore(tempDir.toFile()));
        assertEquals("existing", Files.readString(keystore));
    }

    @Test
    void parsePresetFileContentReturnsAnEmptyMapWithoutConnectionSettings(@TempDir Path tempDir) throws IOException {
        // given
        final Path commentsOnly = tempDir.resolve("comments.yaml");
        Files.writeString(commentsOnly, "# just a comment");
        final Path withoutConnectionSettings = tempDir.resolve("incomplete.yaml");
        Files.writeString(withoutConnectionSettings, "language: de");

        // when, then
        assertTrue(parsePresetFileContent(commentsOnly.toFile()).isEmpty());
        assertTrue(parsePresetFileContent(withoutConnectionSettings.toFile()).isEmpty());
        assertTrue(parsePresetFileContent(tempDir.resolve("missing.yaml").toFile()).isEmpty());
    }

    @Test
    void detectsConnectionSettings() {
        // when, then
        assertTrue(hasConnectionSettings(Map.of("host", "localhost", "port", "8080")));
        assertTrue(hasConnectionSettings(Collections.singletonMap("tokenServerUrl", "https://foo.bar")));
        assertTrue(hasConnectionSettings(Collections.singletonMap("backupTokenServerUrls", "https://foo.bar")));
        assertFalse(hasConnectionSettings(Collections.singletonMap("host", "localhost")));
        assertFalse(hasConnectionSettings(Collections.singletonMap("port", "8080")));
        assertFalse(hasConnectionSettings(Collections.emptyMap()));
    }

}
