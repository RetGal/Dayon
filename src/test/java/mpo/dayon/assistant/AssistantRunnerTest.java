package mpo.dayon.assistant;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.stream.Stream;

import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.*;

class AssistantRunnerTest {

    private static Locale defaultLocale;

    @BeforeAll
    static void getLocale() {
        defaultLocale = Locale.getDefault();
    }

    @AfterAll
    static void resetLocale() {
        Locale.setDefault(defaultLocale);
    }

    @Test
    void shouldSetLocale() {
        // given
        String italian = "it";
        AssistantRunner runner = new AssistantRunner();
        // when
        runner.main(Stream.of(format("lang=%s", italian)).toArray(String[]::new));
        // then
        assertEquals(italian, Locale.getDefault().toLanguageTag(), "Did not set the expected language");
    }
}