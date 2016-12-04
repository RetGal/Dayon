package mpo.dayon.assistant.gui;

import mpo.dayon.assistant.network.NetworkAssistantEngine;
import mpo.dayon.assistant.resource.ImageNames;
import mpo.dayon.common.babylon.Babylon;
import mpo.dayon.common.gui.common.ImageUtilities;

import javax.swing.*;
import java.awt.event.ActionEvent;

public class AssistantStopAction extends AbstractAction
{
    private final NetworkAssistantEngine networkEngine;

    public AssistantStopAction(NetworkAssistantEngine networkEngine)
    {
        setEnabled(false);

        this.networkEngine = networkEngine;

        putValue(Action.NAME, "stop");
        putValue(Action.SHORT_DESCRIPTION, Babylon.translate("stop.session"));
        putValue(Action.SMALL_ICON, ImageUtilities.getOrCreateIcon(ImageNames.STOP));
    }

    public void actionPerformed(ActionEvent ev)
    {
        networkEngine.cancel();
    }

}