package mpo.dayon.common.gui.common;

import mpo.dayon.common.preference.Preferences;

import java.awt.*;
import java.util.Objects;

class FrameConfiguration {

    private int version = 1;
    private final Position position;
    private final Dimension dimension;

    private static final String PREF_VERSION_SUFFIX = ".frame.version";
    private static final String PREF_X_SUFFIX = ".frame.x";
    private static final String PREF_Y_SUFFIX = ".frame.y";
    private static final String PREF_WIDTH_SUFFIX = ".frame.width";
    private static final String PREF_HEIGHT_SUFFIX = ".frame.height";

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

    FrameConfiguration(FrameType type) {
        final Preferences prefs = Preferences.getPreferences();
        version = prefs.getIntPreference(type.getPrefix() + PREF_VERSION_SUFFIX, 0);
        position = new Position(prefs.getIntPreference(type.getPrefix() + PREF_X_SUFFIX, 100), prefs.getIntPreference(type.getPrefix() + PREF_Y_SUFFIX, 100));
        if (type.equals(FrameType.ASSISTANT)) {
            dimension = new Dimension(prefs.getIntPreference(type.getPrefix() + PREF_WIDTH_SUFFIX, 800), prefs.getIntPreference(type.getPrefix() + PREF_HEIGHT_SUFFIX, 600));
        } else {
            dimension = new Dimension(prefs.getIntPreference(type.getPrefix() + PREF_WIDTH_SUFFIX, 550), prefs.getIntPreference(type.getPrefix() + PREF_HEIGHT_SUFFIX, 200));
        }
    }

    FrameConfiguration(Position position, Dimension dimension) {
        this.position = position;
        this.dimension = dimension;
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final FrameConfiguration that = (FrameConfiguration) o;

        return dimension.height == Objects.requireNonNull(that).getHeight() && dimension.width == that.getWidth() && position.getX() == that.getX() && position.getY() == that.getY();
    }

    public int hashCode() {
        int result = position.getX();
        result = 31 * result + position.getY();
        result = 31 * result + dimension.width;
        result = 31 * result + dimension.height;
        return result;
    }

    public void persist(FrameType type) {
        final Preferences.Props props = new Preferences.Props();
        props.set(type.getPrefix() + PREF_VERSION_SUFFIX, String.valueOf(version));
        props.set(type.getPrefix() + PREF_X_SUFFIX, String.valueOf(getX()));
        props.set(type.getPrefix() + PREF_Y_SUFFIX, String.valueOf(getY()));
        props.set(type.getPrefix() + PREF_WIDTH_SUFFIX, String.valueOf(dimension.width));
        props.set(type.getPrefix() + PREF_HEIGHT_SUFFIX, String.valueOf(dimension.height));

        Preferences.getPreferences().update(props); // atomic (!)
    }

}
