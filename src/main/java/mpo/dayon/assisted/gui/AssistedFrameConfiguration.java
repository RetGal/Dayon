package mpo.dayon.assisted.gui;

import mpo.dayon.common.gui.common.Position;
import mpo.dayon.common.gui.common.FrameConfiguration;

import java.awt.*;

public class AssistedFrameConfiguration extends FrameConfiguration {

    /**
     * Default : takes its values from the current preferences.
     *
     * @see mpo.dayon.common.preference.Preferences
     */
    AssistedFrameConfiguration() {
        super.assistedFrameConfiguration();
    }

    AssistedFrameConfiguration(Position position, Dimension dimension) {
        super.assistedFrameConfiguration(position, dimension);
    }

}
