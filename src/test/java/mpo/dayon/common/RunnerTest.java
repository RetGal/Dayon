package mpo.dayon.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
        String[] args = {"lang=en,", "foo=BAR", "spam"};
        // when
        Map<String, String> programArgs = extractProgramArgs(args);
        // then
        assertEquals(2, programArgs.size(), "Unexpected number of extracted ProgramArgs");
        assertEquals("en", programArgs.get("lang"), "Key 'lang' should have value 'en'");
        assertEquals("BAR", programArgs.get("foo"), "Key 'foo' should have value 'BAR'");
    }

    @Test
    void shouldSetDebug() {
        // given
        String[] args = {"debug"};
        assertNull(System.getProperty("dayon.debug"));
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
}
