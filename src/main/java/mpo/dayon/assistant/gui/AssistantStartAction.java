package mpo.dayon.assistant.gui;

import mpo.dayon.common.babylon.Babylon;
import mpo.dayon.common.gui.common.ImageNames;
import mpo.dayon.common.gui.common.ImageUtilities;

import javax.swing.*;
import java.awt.event.ActionEvent;

class AssistantStartAction extends AbstractAction {
    private final transient Assistant assistant;

    public AssistantStartAction(Assistant assistant) {
        this.assistant = assistant;

        putValue(Action.NAME, "start");
        putValue(Action.SHORT_DESCRIPTION, Babylon.translate("start.session"));
        putValue(Action.SMALL_ICON, ImageUtilities.getOrCreateIcon(ImageNames.START));
    }

    @Override
    public void actionPerformed(ActionEvent ev) {
        assistant.startNetwork();
    }
}
