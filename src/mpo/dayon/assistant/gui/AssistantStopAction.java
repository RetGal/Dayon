package mpo.dayon.assistant.gui;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.Action;

import mpo.dayon.assistant.network.NetworkAssistantEngine;
import mpo.dayon.assistant.resource.ImageNames;
import mpo.dayon.common.babylon.Babylon;
import mpo.dayon.common.gui.common.ImageUtilities;

public class AssistantStopAction extends AbstractAction {
	private static final long serialVersionUID = 8396252577114773282L;
	private final NetworkAssistantEngine networkEngine;

	public AssistantStopAction(NetworkAssistantEngine networkEngine) {
		setEnabled(false);

		this.networkEngine = networkEngine;

		putValue(Action.NAME, "stop");
		putValue(Action.SHORT_DESCRIPTION, Babylon.translate("stop.session"));
		putValue(Action.SMALL_ICON, ImageUtilities.getOrCreateIcon(ImageNames.STOP));
	}

	public void actionPerformed(ActionEvent ev) {
		networkEngine.cancel();
	}

}