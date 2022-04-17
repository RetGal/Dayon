package mpo.dayon.assistant.gui;

import mpo.dayon.common.gui.common.ImageNames;

import javax.swing.AbstractAction;
import javax.swing.Action;
import java.awt.event.ActionEvent;

import static mpo.dayon.common.babylon.Babylon.translate;
import static mpo.dayon.common.gui.common.ImageUtilities.getOrCreateIcon;

class AssistantStopAction extends AbstractAction {
    private final transient Assistant assistant;

    AssistantStopAction(Assistant assistant) {
        setEnabled(false);

        this.assistant = assistant;

        putValue(Action.NAME, "stop");
        putValue(Action.SHORT_DESCRIPTION, translate("stop.session"));
        putValue(Action.SMALL_ICON, getOrCreateIcon(ImageNames.STOP));
    }

    @Override
    public void actionPerformed(ActionEvent ev) {
        assistant.stopNetwork();
    }

}