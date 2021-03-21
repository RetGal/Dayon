package mpo.dayon.common.gui.toolbar;

import java.awt.Insets;

import javax.swing.Action;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;

public class ToolBar extends JToolBar {
	public static final Insets ZERO_INSETS = new Insets(1, 1, 1, 1);

	public ToolBar() {
		setFloatable(false);
		setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));
	}

	public void addAction(Action action) {
		final JButton button = new JButton();
		button.setMargin(ZERO_INSETS);
		button.setHideActionText(true);
		button.setAction(action);
		if (action.getValue(Action.SMALL_ICON) == null) {
			button.setText((String) action.getValue("DISPLAY_NAME"));
		}
		button.setFocusable(false);
		add(button);
	}

	public void addToggleAction(Action action) {
		final JToggleButton button = new JToggleButton();
		button.setMargin(ZERO_INSETS);
		button.setHideActionText(true);
		button.setAction(action);
		if (action.getValue(Action.SMALL_ICON) == null) {
			button.setText((String) action.getValue("DISPLAY_NAME"));
		}
		button.setFocusable(false);
		add(button);
	}

	public void addGlue() {
		add(Box.createHorizontalGlue());
	}
}
