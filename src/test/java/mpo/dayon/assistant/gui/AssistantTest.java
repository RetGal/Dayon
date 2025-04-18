package mpo.dayon.assistant.gui;

import org.junit.jupiter.api.Test;

import java.awt.*;

import static mpo.dayon.common.configuration.Configuration.DEFAULT_TOKEN_SERVER_URL;
import static org.junit.jupiter.api.Assertions.*;

class AssistantTest {

    @Test
    void withCustomTokenServerUrlFromYaml() {
        if (!GraphicsEnvironment.isHeadless()) {
            // given
            Assistant assistant = new Assistant("https://rvs.example.com", null);

            // when then
            assertTrue(assistant.hasTokenServerUrlFromYaml());
            assertEquals("https://rvs.example.com", System.getProperty("dayon.custom.tokenServer"));
        }
    }

    @Test
    void withDefaultTokenServerUrlFromYaml() {
        if (!GraphicsEnvironment.isHeadless()) {
            // given
            Assistant assistant = new Assistant(DEFAULT_TOKEN_SERVER_URL, null);

            // when then
            assertTrue(assistant.hasTokenServerUrlFromYaml());
            assertNull(System.getProperty("dayon.custom.tokenServer"));
        }
    }

    @Test
    void withoutTokenServerUrl() {
        if (!GraphicsEnvironment.isHeadless()) {
            // given
            Assistant assistant = new Assistant(null, null);

            // when then
            assertFalse(assistant.hasTokenServerUrlFromYaml());
            assertNull(System.getProperty("dayon.custom.tokenServer"));
        }
    }
}