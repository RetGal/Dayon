package mpo.dayon.assistant.gui;

import mpo.dayon.common.gui.common.Position;
import mpo.dayon.common.gui.common.FrameConfiguration;

import java.awt.*;

public class AssistantFrameConfiguration extends FrameConfiguration {

    /**
     * Default : takes its values from the current preferences.
     *
     * @see mpo.dayon.common.preference.Preferences
     */
    AssistantFrameConfiguration() {
        super.assistantFrameConfiguration();
    }

    AssistantFrameConfiguration(Position position, Dimension dimension) {
        super.assistantFrameConfiguration(position, dimension);
    }

}
