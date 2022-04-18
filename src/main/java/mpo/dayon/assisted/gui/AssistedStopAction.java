package mpo.dayon.assisted.gui;

import mpo.dayon.common.gui.common.ImageNames;
import mpo.dayon.common.gui.common.ImageUtilities;

import javax.swing.*;
import java.awt.event.ActionEvent;

import static mpo.dayon.common.babylon.Babylon.translate;

class AssistedStopAction extends AbstractAction {
	private final transient Assisted assisted;

	AssistedStopAction(Assisted assisted) {
		setEnabled(false);

		this.assisted = assisted;

		putValue(Action.NAME, "stop");
		putValue(Action.SHORT_DESCRIPTION, translate("stop.session"));
		putValue(Action.SMALL_ICON, ImageUtilities.getOrCreateIcon(ImageNames.STOP));
	}

	@Override
    public void actionPerformed(ActionEvent ev) {
		assisted.stop();
	}
}