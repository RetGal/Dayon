package mpo.dayon.common.gui.toolbar;

import java.awt.Insets;

import javax.swing.*;

public class ToolBar extends JToolBar {
	public static final Insets ZERO_INSETS = new Insets(1, 1, 1, 1);

	public ToolBar() {
		setFloatable(false);
		setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));
	}

	public void addAction(Action action) {
		final JToggleButton button = createToggleButton(action);
		button.setDisabledIcon(null);
		add(button);
	}

	public void addToggleAction(Action action) {
		final JToggleButton button = createToggleButton(action);
		add(button);
	}

	private JToggleButton createToggleButton(Action action) {
		final JToggleButton button = new JToggleButton();
		button.setMargin(ZERO_INSETS);
		button.setHideActionText(true);
		button.setAction(action);
		if (action.getValue(Action.SMALL_ICON) == null) {
			button.setText((String) action.getValue("DISPLAY_NAME"));
		}
		button.setFocusable(false);
		return button;
	}

	public void addGlue() {
		add(Box.createHorizontalGlue());
	}
}
