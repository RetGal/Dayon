package mpo.dayon.common.gui.toolbar;

import mpo.dayon.common.gui.common.ImageNames;

import java.awt.*;

import javax.swing.*;

import static mpo.dayon.common.babylon.Babylon.translate;
import static mpo.dayon.common.gui.common.ImageUtilities.getOrCreateIcon;

public class ToolBar extends JToolBar {
	public static final Insets ZERO_INSETS = new Insets(1, 1, 1, 1);

	private final JLabel message = new JLabel();

	public ToolBar() {
		setFloatable(false);
		setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));
		setBorder(null);
	}

	public void addAction(Action action) {
		final JButton button = new JButton();
		addButtonProperties(action, button);
		button.setDisabledIcon(null);
		add(button);
	}

	public void addToggleAction(Action action) {
		final JToggleButton button = new JToggleButton();
		addButtonProperties(action, button);
		add(button);
	}

	private void addButtonProperties(Action action, AbstractButton button) {
		button.setMargin(ZERO_INSETS);
		button.setHideActionText(true);
		button.setAction(action);
		if (action.getValue(Action.SMALL_ICON) == null) {
			button.setText((String) action.getValue("DISPLAY_NAME"));
		}
		button.setFocusable(false);
	}

	public void addGlue() {
		add(Box.createHorizontalGlue());
	}

	public JLabel getMessage() {
		return message;
	}

	public void clearMessage() {
		this.message.setIcon(null);
		this.message.setText(null);
		this.message.setToolTipText(null);
	}

	public void setMessage(String fingerprint) {
		this.message.setIcon(getOrCreateIcon(ImageNames.WARNING));
		this.message.setText(translate("connection.suspicious"));
		this.message.setToolTipText(translate("connection.fingerprint.mismatch", fingerprint));
	}

}
