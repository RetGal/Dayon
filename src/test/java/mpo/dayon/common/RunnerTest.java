package mpo.dayon.common;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Locale;
import java.util.Map;

import static mpo.dayon.common.Runner.*;
import static org.junit.jupiter.api.Assertions.*;

class RunnerTest {

    @Test
    void shouldSetExpectedLocale() {
        // given
        String lang = "fr";
        String before = Locale.getDefault().toLanguageTag();
        if (before.equals(lang)) {
            lang = "de";
        }
        // when
        overrideLocale(lang);
        // then
        assertEquals(lang, Locale.getDefault().toLanguageTag());
    }

    @Test
    void shouldNotSetUnsupportedLanguage() {
        // given
        String lang = "cn";
        String before = Locale.getDefault().toLanguageTag();
        if (before.equals(lang)) {
            lang = "ru";
        }
        // when
        overrideLocale(lang);
        // then
        assertEquals(before, Locale.getDefault().toLanguageTag());
    }

    @Test
    void shouldExtractProgramArgs() {
        // given
        String[] args = {"lang=en,", "foo=BAR", "spam"};
        // when
        Map<String, String> programArgs = extractProgramArgs(args);
        // then
        assertEquals(2, programArgs.size());
        assertEquals("en", programArgs.get("lang"));
        assertEquals("BAR", programArgs.get("foo"));
    }

    @Test
    void shouldSetDebug() {
        // given
        String[] args = {"debug"};
        assertNull(System.getProperty("dayon.debug"));
        // when
        setDebug(args);
        // then
        assertEquals("on", System.getProperty("dayon.debug"));
    }

    @Test
    void shouldGetOrCreateAppHomeDir() {
        // when
        final File appHomeDir = getOrCreateAppHomeDir();
        // then
        assertNotNull(appHomeDir);
        assertTrue(appHomeDir.getName().endsWith(".dayon"));
    }
}
