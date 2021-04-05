package mpo.dayon.assisted.gui;

import mpo.dayon.common.babylon.Babylon;
import mpo.dayon.common.gui.common.ImageNames;
import mpo.dayon.common.gui.common.ImageUtilities;

import javax.swing.*;
import java.awt.event.ActionEvent;

class AssistedStopAction extends AbstractAction {
	private final transient Assisted assisted;

	public AssistedStopAction(Assisted assisted) {
		setEnabled(false);

		this.assisted = assisted;

		putValue(Action.NAME, "stop");
		putValue(Action.SHORT_DESCRIPTION, Babylon.translate("stop.session"));
		putValue(Action.SMALL_ICON, ImageUtilities.getOrCreateFixedScaleIcon(ImageNames.STOP));
	}

	@Override
    public void actionPerformed(ActionEvent ev) {
		assisted.stop();
	}
}