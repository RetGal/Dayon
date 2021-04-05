package mpo.dayon.assistant.gui;

import mpo.dayon.common.babylon.Babylon;
import mpo.dayon.common.gui.common.ImageNames;
import mpo.dayon.common.gui.common.ImageUtilities;

import javax.swing.*;
import java.awt.event.ActionEvent;

class AssistantStopAction extends AbstractAction {
    private final transient Assistant assistant;

    public AssistantStopAction(Assistant assistant) {
        setEnabled(false);

        this.assistant = assistant;

        putValue(Action.NAME, "stop");
        putValue(Action.SHORT_DESCRIPTION, Babylon.translate("stop.session"));
        putValue(Action.SMALL_ICON, ImageUtilities.getOrCreateFixedScaleIcon(ImageNames.STOP));
    }

    @Override
    public void actionPerformed(ActionEvent ev) {
        assistant.stopNetwork();
    }

}