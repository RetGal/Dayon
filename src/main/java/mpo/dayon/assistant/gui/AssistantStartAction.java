package mpo.dayon.assistant.gui;

import mpo.dayon.common.gui.common.ImageNames;

import javax.swing.AbstractAction;
import javax.swing.Action;
import java.awt.event.ActionEvent;

import static mpo.dayon.common.babylon.Babylon.translate;
import static mpo.dayon.common.gui.common.ImageUtilities.getOrCreateIcon;

class AssistantStartAction extends AbstractAction {
    private final transient Assistant assistant;

    AssistantStartAction(Assistant assistant) {
        this.assistant = assistant;

        putValue(Action.NAME, "start");
        putValue(Action.SHORT_DESCRIPTION, translate("start.session"));
        putValue(Action.SMALL_ICON, getOrCreateIcon(ImageNames.START));
    }

    @Override
    public void actionPerformed(ActionEvent ev) {
        assistant.startNetwork();
    }
}
