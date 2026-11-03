package mpo.dayon.common.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class LanguageTest {

    @ParameterizedTest
    @CsvSource({"DE, de", "EN, en", "ES, es", "FR, fr", "IT, it", "RU, ru", "SV, sv", "TR, tr", "ZH, zh"})
    void getShortName(Language language, String expectedShortName) {
        // given, when, then
        assertEquals(expectedShortName, language.getShortName());
    }

    @Test
    void everyLanguageHasADistinctShortName() {
        // given, when, then
        assertEquals(9, Language.values().length);
        assertEquals(Language.values().length, java.util.Arrays.stream(Language.values()).map(Language::getShortName).distinct().count());
    }

    @Test
    void namesAreTranslated() {
        // given, when, then
        assertEquals("Deutsch", Language.DE.getName());
        assertEquals("English", Language.EN.getName());
        assertEquals("简体中文", Language.ZH.getName());
    }
}
