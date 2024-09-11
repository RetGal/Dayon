package mpo.dayon.common.babylon;

import mpo.dayon.common.capture.Gray8Bits;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class BabylonTest {

    @Test
    void shouldTranslate() {
        // given
        Locale defaultLocale = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("de"));

        // when, then
        assertEquals("Assistent", Babylon.translate("assistant"));

        Locale.setDefault(defaultLocale);
    }

    @Test
    void shouldTranslateWithArguments() {
        // given
        Locale defaultLocale = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("ru"));

        // when, then
        assertEquals("1.1.1.1 ( общедоступный )", Babylon.translate("ipAddressPublic", "1.1.1.1"));

        Locale.setDefault(defaultLocale);
    }

    @Test
    void shouldHandleMissingTranslation() {
        // given
        String tag = "snafu";
        // when, then
        assertEquals(tag, Babylon.translate(tag));
    }

    @Test
    void shouldHandleMissingTranslationsOfEnum() {
        // given
        Locale defaultLocale = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("fr"));

        // when, then
        assertEquals("X_16", Babylon.translateEnum(Gray8Bits.X_16));

        Locale.setDefault(defaultLocale);
    }
}