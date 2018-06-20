package mpo.dayon.common.gui.common;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import org.jetbrains.annotations.Nullable;

import mpo.dayon.common.babylon.Babylon;

public abstract class DialogFactory {
	public interface Validator {
		/**
		 * @return {@code null} if the validation is fine; otherwise an error
		 *         message.
		 */
		@Nullable
		String validate();
	}

	/**
	 * Creates and show a modal dialog with ok/cancel buttons.
	 *
	 * @return {@code true} for {@code OK}, {@code false} for {@code CANCEL}.
	 */
	public static boolean showOkCancel(final Component owner, final String title, final JComponent payloadPane, @Nullable final Validator validator) {
		final JButton ok = new JButton(Babylon.translate("ok"));
		final JButton cancel = new JButton(Babylon.translate("cancel"));

		final JButton[] buttons = new JButton[] { ok, cancel };

		final JDialog dialog = createDialog(owner, title, payloadPane, buttons, ok, true);

		final boolean[] result = new boolean[1];

		ok.addActionListener(ev -> {
            final String validationMessage = (validator == null ? null : validator.validate());
            if (validationMessage == null) {
                result[0] = true;
                dialog.dispose();
            } else {
                JOptionPane.showMessageDialog(dialog, validationMessage, title + " : Error", JOptionPane.ERROR_MESSAGE);
            }
        });

		cancel.addActionListener(ev -> {
            result[0] = false;
            dialog.dispose();
        });

		dialog.setVisible(true);

		return result[0];
	}

	/**
	 * Creates a modal dialog.
	 */
	private static JDialog createDialog(Component owner, String title, JComponent payloadPane, JButton[] buttons, JButton defaultButton,
			boolean hasEscapeButton) {
		final Frame parent = owner instanceof Frame ? (Frame) owner : (Frame) SwingUtilities.getAncestorOfClass(Frame.class, owner);

		payloadPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 15, 10));

		final JPanel payloadPaneEx = new JPanel(new BorderLayout());

		payloadPaneEx.add(payloadPane, BorderLayout.CENTER);
		payloadPaneEx.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));

		final JPanel payloadPaneExEx = new JPanel(new BorderLayout());

		payloadPaneExEx.add(payloadPaneEx, BorderLayout.CENTER);
		payloadPaneExEx.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));

		final JPanel buttonPane = new JPanel();
		buttonPane.setLayout(new BoxLayout(buttonPane, BoxLayout.LINE_AXIS));

		buttonPane.add(Box.createHorizontalGlue());

		for (JButton button : buttons) {
			buttonPane.add(Box.createRigidArea(new Dimension(10, 10)));
			buttonPane.add(button);
		}

		buttonPane.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 10));

		// --

		final JPanel contentPane = new JPanel(new BorderLayout());

		contentPane.add(payloadPaneExEx, BorderLayout.CENTER);
		contentPane.add(buttonPane, BorderLayout.SOUTH);

		// --

		final JDialog dialog = new JDialog(parent, title, true);

		// dialog.setIconImage(ImageFactory.getOrCreateIcon("ladybug.png").getImage());
		dialog.setContentPane(contentPane);
		dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

		// --

		dialog.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				dialog.setVisible(false);
				dialog.dispose();
			}
		});

		// Make this dialog CANCELing on ESCAPE key
		if (hasEscapeButton) {
			final KeyStroke escape = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);

			final InputMap inputMap = dialog.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
			inputMap.put(escape, "ESCAPE");

			final ActionMap actionMap = dialog.getRootPane().getActionMap();
			actionMap.put("ESCAPE", new AbstractAction() {

				public void actionPerformed(ActionEvent e) {
					dialog.setVisible(false);
					dialog.dispose();
				}
			});
		}

		// --

		SwingUtilities.getRootPane(defaultButton).setDefaultButton(defaultButton);

		// --

		dialog.pack();
		dialog.setLocationRelativeTo(parent);

		return dialog;
	}

	public static boolean showYesNo(Component owner, String title, String message) {
		final Object[] options = { Babylon.translate("yes"), Babylon.translate("no"), };

		final int result = JOptionPane.showOptionDialog(owner, message, title, JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, options,
				options[0]);

		return result == 0;
	}

}
