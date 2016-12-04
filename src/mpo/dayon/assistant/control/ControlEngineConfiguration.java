package mpo.dayon.assistant.control;

import mpo.dayon.common.configuration.Configuration;

public class ControlEngineConfiguration extends Configuration
{
    /**
     * Default : takes its values from the current preferences.
     *
     * @see mpo.dayon.common.preference.Preferences
     */
    public ControlEngineConfiguration()
    {
    }

    /**
     * @param clear allows for clearing properties from previous version
     */
    protected void persist(boolean clear)
    {
    }
}
