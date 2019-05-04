package mpo.dayon.common.gui.common;

import mpo.dayon.assistant.gui.AssistantFrameConfiguration;
import mpo.dayon.assisted.gui.AssistedFrameConfiguration;
import mpo.dayon.common.configuration.Configuration;
import mpo.dayon.common.preference.Preferences;

import java.awt.*;
import java.util.Objects;

public class FrameConfiguration extends Configuration {

    private Position position;
    private Dimension dimension;

    private String PREF_VERSION;
    private String PREF_X;
    private String PREF_Y;
    private String PREF_WIDTH;
    private String PREF_HEIGHT;

    public FrameConfiguration() {
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


    public void assistantFrameConfiguration() {
        setAssistantPropertyKeys();

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

    public void assistantFrameConfiguration(Position position, Dimension dimension) {
        setAssistantPropertyKeys();
        this.position = position;
        this.dimension = dimension;
    }

    private void setAssistantPropertyKeys() {
        PREF_VERSION = "assistant.frame.version";
        PREF_X = "assistant.frame.x";
        PREF_Y = "assistant.frame.y";
        PREF_WIDTH = "assistant.frame.width";
        PREF_HEIGHT = "assistant.frame.height";
    }

    public void assistedFrameConfiguration() {
        setAssistedPropertyKeys();

        final Preferences prefs = Preferences.getPreferences();

        final int version = prefs.getIntPreference(PREF_VERSION, 0);

        if (!prefs.isNull() && version == 0) {
            position = new Position(prefs.getIntPreference("assistedFrameX", 100), prefs.getIntPreference("assistedFrameY", 100));
            dimension = new Dimension(prefs.getIntPreference("assistedFrameWidth", 400), prefs.getIntPreference("assistedFrameHeight", 200));
            persist(true);
        } else {
            position = new Position(prefs.getIntPreference(PREF_X, 100), prefs.getIntPreference(PREF_Y, 100));
            dimension = new Dimension(prefs.getIntPreference(PREF_WIDTH, 400), prefs.getIntPreference(PREF_HEIGHT, 200));
        }
    }

    public void assistedFrameConfiguration(Position position, Dimension dimension) {
        setAssistedPropertyKeys();
        this.position = position;
        this.dimension = dimension;
    }

    private void setAssistedPropertyKeys() {
        PREF_VERSION = "assisted.frame.version";
        PREF_X = "assisted.frame.x";
        PREF_Y = "assisted.frame.y";
        PREF_WIDTH = "assisted.frame.width";
        PREF_HEIGHT = "assisted.frame.height";
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final FrameConfiguration that = o instanceof AssistedFrameConfiguration ? ((AssistedFrameConfiguration) o) : ((AssistantFrameConfiguration) o);

        return dimension.height == Objects.requireNonNull(that).getHeight() && dimension.width == that.getWidth() && position.getX() == that.getX() && position.getY() == that.getY();
    }

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
    public void persist(boolean clear) {
        final Preferences.Props props = new Preferences.Props();
        props.set(PREF_VERSION, String.valueOf(1));
        props.set(PREF_X, String.valueOf(getX()));
        props.set(PREF_Y, String.valueOf(getY()));
        props.set(PREF_WIDTH, String.valueOf(dimension.width));
        props.set(PREF_HEIGHT, String.valueOf(dimension.height));

        // migration support (!)
        if (clear) {
            props.clear("assistantFrameX");
            props.clear("assistantFrameY");
            props.clear("assistantFrameWidth");
            props.clear("assistantFrameHeight");
            props.clear("assistedFrameX");
            props.clear("assistedFrameY");
            props.clear("assistedFrameWidth");
            props.clear("assistedFrameHeight");
        }
        Preferences.getPreferences().update(props); // atomic (!)
    }

}
