package mpo.dayon.common.gui.common;

import mpo.dayon.common.preference.Preferences;
import org.junit.jupiter.api.Test;

import java.awt.Dimension;

import static org.junit.jupiter.api.Assertions.*;

class FrameConfigurationTest {

    @Test
    void readsTheConfigurationFromThePreferences() {
        // given
        final Preferences preferences = Preferences.getPreferences();

        // when
        final FrameConfiguration assistant = new FrameConfiguration(FrameType.ASSISTANT);
        final FrameConfiguration assisted = new FrameConfiguration(FrameType.ASSISTED);

        // then
        assertEquals(preferences.getIntPreference("assistant.frame.x", 100), assistant.getX());
        assertEquals(preferences.getIntPreference("assistant.frame.y", 100), assistant.getY());
        assertEquals(preferences.getIntPreference("assistant.frame.width", FrameType.ASSISTANT.getMinWidth()), assistant.getWidth());
        assertEquals(preferences.getIntPreference("assistant.frame.height", FrameType.ASSISTANT.getMinHeight()), assistant.getHeight());
        assertEquals(preferences.getIntPreference("assisted.frame.x", 100), assisted.getX());
        assertEquals(preferences.getIntPreference("assisted.frame.width", FrameType.ASSISTED.getMinWidth()), assisted.getWidth());
        assertEquals(preferences.getIntPreference("assisted.frame.height", FrameType.ASSISTED.getMinHeight()), assisted.getHeight());
    }

    @Test
    void holdsAnExplicitPositionAndDimension() {
        // when
        final FrameConfiguration configuration = new FrameConfiguration(new Position(11, 22), new Dimension(555, 666));

        // then
        assertEquals(11, configuration.getX());
        assertEquals(22, configuration.getY());
        assertEquals(555, configuration.getWidth());
        assertEquals(666, configuration.getHeight());
    }

    @Test
    void fallsBackToTheAssistedMinimumDimension() {
        // when
        final FrameConfiguration configuration = new FrameConfiguration(new Position(11, 22), null);

        // then
        assertEquals(FrameType.ASSISTED.getMinWidth(), configuration.getWidth());
        assertEquals(FrameType.ASSISTED.getMinHeight(), configuration.getHeight());
    }

    @Test
    void equalsAndHashCode() {
        // given
        final FrameConfiguration first = new FrameConfiguration(new Position(11, 22), new Dimension(555, 666));
        final FrameConfiguration second = new FrameConfiguration(new Position(11, 22), new Dimension(555, 666));

        // when, then
        assertNotNull(first);
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(new FrameConfiguration(new Position(12, 22), new Dimension(555, 666)), first);
        assertNotEquals(new FrameConfiguration(new Position(11, 23), new Dimension(555, 666)), first);
        assertNotEquals(new FrameConfiguration(new Position(11, 22), new Dimension(556, 666)), first);
        assertNotEquals(new FrameConfiguration(new Position(11, 22), new Dimension(555, 667)), first);
        assertNotEquals(new Object(), first);
    }

    @Test
    void persistAndReload() {
        // given
        final FrameConfiguration original = new FrameConfiguration(FrameType.ASSISTED);
        final FrameConfiguration configuration = new FrameConfiguration(new Position(11, 22), new Dimension(555, 666));

        try {
            // when
            configuration.persist(FrameType.ASSISTED);

            // then
            final FrameConfiguration reloaded = new FrameConfiguration(FrameType.ASSISTED);
            assertEquals(configuration, reloaded);
            assertEquals(555, reloaded.getWidth());
            assertEquals(666, reloaded.getHeight());
        } finally {
            original.persist(FrameType.ASSISTED);
        }
    }
}
