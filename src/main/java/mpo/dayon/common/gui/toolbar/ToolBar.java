package mpo.dayon.common.gui.toolbar;

import java.awt.Insets;

import javax.swing.*;

public class ToolBar extends JToolBar {
	public static final Insets ZERO_INSETS = new Insets(1, 1, 1, 1);

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
}
