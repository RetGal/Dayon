package mpo.dayon.common.babylon;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class BabylonTest {

    @Test
    void shouldTranslate() {
        // given
        Locale defaultLocale = Locale.getDefault();
        Locale.setDefault(new Locale("de"));

        // when, then
        assertEquals("Assistent", Babylon.translate("assistant"));

        Locale.setDefault(defaultLocale);
    }
}