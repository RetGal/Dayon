package mpo.dayon.assistant.gui;

import mpo.dayon.common.configuration.Configuration;
import mpo.dayon.common.gui.common.Position;
import mpo.dayon.common.preference.Preferences;

import java.awt.*;

public class AssistantFrameConfiguration extends Configuration {

    private static final String PREF_VERSION = "assistant.frame.version";
    private static final String PREF_X = "assistant.frame.x";
    private static final String PREF_Y = "assistant.frame.y";
    private static final String PREF_WIDTH = "assistant.frame.width";
    private static final String PREF_HEIGHT = "assistant.frame.height";

    private final Position position;
    private final Dimension dimension;

    /**
     * Default : takes its values from the current preferences.
     *
     * @see mpo.dayon.common.preference.Preferences
     */
    AssistantFrameConfiguration() {
        final Preferences prefs = Preferences.getPreferences();
        final int version = prefs.getIntPreference(PREF_VERSION, 0);

        if (!prefs.isNull() && version == 0) {
            position = new Position(prefs.getIntPreference("assistantFrameX", 100), prefs.getIntPreference("assistantFrameY", 100));
            dimension = new Dimension(prefs.getIntPreference("assistantFrameWidth", 800), prefs.getIntPreference("assistantFrameHeight", 600));
            persist(true);
        } else {
            position = new Position(prefs.getIntPreference(PREF_X, 100), prefs.getIntPreference(PREF_Y, 100));
            dimension = new Dimension(prefs.getIntPreference(PREF_WIDTH, 800), prefs.getIntPreference(PREF_HEIGHT, 600));
        }
    }

    AssistantFrameConfiguration(Position position, Dimension dimension) {
        this.position = position;
        this.dimension = dimension;
    }

    public int getX() {
        return position.getX();
    }

    public int getY() {
        return position.getY();
    }

    public int getWidth() {
        return dimension.width;
    }

    public int getHeight() {
        return dimension.height;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AssistantFrameConfiguration that = (AssistantFrameConfiguration) o;
        return dimension.height == that.dimension.height && dimension.width == that.dimension.width && position.getX() == that.position.getX() && position.getY() == that.position.getY();
    }

    @Override
    public int hashCode() {
        int result = position.getX();
        result = 31 * result + position.getY();
        result = 31 * result + dimension.width;
        result = 31 * result + dimension.height;
        return result;
    }

    /**
     * @param clear allows for clearing properties from previous version
     */
    @Override
    protected void persist(boolean clear) {
        final Preferences.Props props = new Preferences.Props();
        props.set(PREF_VERSION, String.valueOf(1));
        props.set(PREF_X, String.valueOf(position.getX()));
        props.set(PREF_Y, String.valueOf(position.getY()));
        props.set(PREF_WIDTH, String.valueOf(dimension.width));
        props.set(PREF_HEIGHT, String.valueOf(dimension.height));

        // migration support (!)
        if (clear) {
            props.clear("assistantFrameX");
            props.clear("assistantFrameY");
            props.clear("assistantFrameWidth");
            props.clear("assistantFrameHeight");
        }
        Preferences.getPreferences().update(props); // atomic (!)
    }

}
