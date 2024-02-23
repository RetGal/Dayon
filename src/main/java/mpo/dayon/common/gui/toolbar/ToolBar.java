package mpo.dayon.common.gui.toolbar;

import java.awt.*;

import javax.swing.*;

public class ToolBar extends JToolBar {
	public static final Insets ZERO_INSETS = new Insets(1, 1, 1, 1);

	public static final Font DEFAULT_FONT = new Font("Sans Serif", Font.PLAIN, 16);

	public static final Component DEFAULT_SPACER = Box.createHorizontalStrut(8);

	public static final String DISPLAY_NAME = "DISPLAY_NAME";

	public ToolBar() {
		setFloatable(false);
		setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));
		setBorder(BorderFactory.createEmptyBorder());
	}

	public void addAction(Action action) {
		addAction(action, Component.CENTER_ALIGNMENT);
	}

	public void addAction(Action action, float alignmentY) {
		final JButton button = new JButton();
		addButtonProperties(action, button);
		button.setAlignmentY(alignmentY);
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
		button.setFont(DEFAULT_FONT);
		button.setText((String) action.getValue(DISPLAY_NAME));
		button.setFocusable(false);
		button.setDisabledIcon(null);
	}

	public void addGlue() {
		add(Box.createHorizontalGlue());
	}
}