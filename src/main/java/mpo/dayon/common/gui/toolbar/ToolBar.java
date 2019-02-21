package mpo.dayon.common.gui.toolbar;

import java.awt.Insets;

import javax.swing.Action;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;

public class ToolBar extends JToolBar {
	private static final Insets zeroInsets = new Insets(1, 1, 1, 1);

	public ToolBar() {
		setFloatable(false);
		setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));
	}

	public void addAction(Action action) {
		final JButton button = new JButton();

		button.setMargin(zeroInsets);
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

		button.setMargin(zeroInsets);
		button.setHideActionText(true);
		button.setAction(action);

		if (action.getValue(Action.SMALL_ICON) == null) {
			button.setText((String) action.getValue("DISPLAY_NAME"));
		}

		button.setFocusable(false);

		add(button);
	}

	@Override
	public void addSeparator() {
		super.addSeparator();
	}

	public void addGlue() {
		add(Box.createHorizontalGlue());
	}
}
