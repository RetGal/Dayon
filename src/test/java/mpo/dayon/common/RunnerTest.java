package mpo.dayon.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Locale;
import java.util.Map;

import static mpo.dayon.common.Runner.*;
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
        overrideLocale(lang);
        // then
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
        overrideLocale(lang);
        // then
        assertEquals(defaultLocale.toLanguageTag(), Locale.getDefault().toLanguageTag(), "Locale shouldn't have been altered");
    }

    @Test
    void shouldIgnoreNull() {
        // given
        // when
        overrideLocale(null);
        // then
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
        assertEquals("on", System.getProperty("dayon.debug"), "Debug should have been activated");
    }

    @Test
    void shouldGetOrCreateAppHomeDir() {
        // when
        final File appHomeDir = getOrCreateAppHomeDir();
        // then
        assertNotNull(appHomeDir);
        assertTrue(appHomeDir.getName().endsWith(".dayon"), "No AppHomeDir found");
    }
}
