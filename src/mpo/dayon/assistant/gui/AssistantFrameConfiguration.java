package mpo.dayon.assistant.gui;

import mpo.dayon.common.configuration.Configuration;
import mpo.dayon.common.preference.Preferences;

public class AssistantFrameConfiguration extends Configuration
{
    private static final String PREF_VERSION = "assistant.frame.version";

    private static final String PREF_X = "assistant.frame.x";

    private static final String PREF_Y = "assistant.frame.y";

    private static final String PREF_WIDTH = "assistant.frame.width";

    private static final String PREF_HEIGHT = "assistant.frame.height";

    private final int x;

    private final int y;

    private final int width;

    private final int height;

    /**
     * Default : takes its values from the current preferences.
     *
     * @see mpo.dayon.common.preference.Preferences
     */
    public AssistantFrameConfiguration()
    {
        final Preferences prefs = Preferences.getPreferences();

        final int version = prefs.getIntPreference(PREF_VERSION, 0);

        if (!prefs.isNull() && version == 0)
        {
            x = prefs.getIntPreference("assistantFrameX", 100);
            y = prefs.getIntPreference("assistantFrameY", 100);
            width = prefs.getIntPreference("assistantFrameWidth", 800);
            height = prefs.getIntPreference("assistantFrameHeight", 600);

            persist(true);
        }
        else
        {
            x = prefs.getIntPreference(PREF_X, 100);
            y = prefs.getIntPreference(PREF_Y, 100);
            width = prefs.getIntPreference(PREF_WIDTH, 800);
            height = prefs.getIntPreference(PREF_HEIGHT, 600);
        }
    }

    public AssistantFrameConfiguration(int x, int y, int width, int height)
    {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public int getX()
    {
        return x;
    }

    public int getY()
    {
        return y;
    }

    public int getWidth()
    {
        return width;
    }

    public int getHeight()
    {
        return height;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o == null || getClass() != o.getClass())
        {
            return false;
        }

        final AssistantFrameConfiguration that = (AssistantFrameConfiguration) o;

        return height == that.height && width == that.width && x == that.x && y == that.y;
    }

    @Override
    public int hashCode()
    {
        int result = x;
        result = 31 * result + y;
        result = 31 * result + width;
        result = 31 * result + height;
        return result;
    }

    /**
     * @param clear allows for clearing properties from previous version
     */
    protected void persist(boolean clear)
    {
        final Preferences.Props props = new Preferences.Props();
        {
            props.set(PREF_VERSION, String.valueOf(1));
            props.set(PREF_X, String.valueOf(x));
            props.set(PREF_Y, String.valueOf(y));
            props.set(PREF_WIDTH, String.valueOf(width));
            props.set(PREF_HEIGHT, String.valueOf(height));

            if (clear) // migration support (!)
            {
                props.clear("assistantFrameX");
                props.clear("assistantFrameY");
                props.clear("assistantFrameWidth");
                props.clear("assistantFrameHeight");

                props.clear("assistedFrameX");
                props.clear("assistedFrameY");
                props.clear("assistedFrameWidth");
                props.clear("assistedFrameHeight");
            }
        }

        Preferences.getPreferences().update(props); // atomic (!)
    }

}
