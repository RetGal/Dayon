package mpo.dayon.assistant.gui;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.Action;

import mpo.dayon.assistant.network.NetworkAssistantEngine;
import mpo.dayon.assistant.resource.ImageNames;
import mpo.dayon.common.babylon.Babylon;
import mpo.dayon.common.gui.common.ImageUtilities;

public class AssistantStartAction extends AbstractAction {
	private final NetworkAssistantEngine networkEngine;

	public AssistantStartAction(NetworkAssistantEngine networkEngine) {
		this.networkEngine = networkEngine;

		putValue(Action.NAME, "start");
		putValue(Action.SHORT_DESCRIPTION, Babylon.translate("start.session"));
		putValue(Action.SMALL_ICON, ImageUtilities.getOrCreateIcon(ImageNames.START));
	}

	public void actionPerformed(ActionEvent ev) {
		networkEngine.start();
	}
}
