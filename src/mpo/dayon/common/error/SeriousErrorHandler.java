package mpo.dayon.common.error;

import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.jetbrains.annotations.Nullable;

import mpo.dayon.common.babylon.Babylon;
import mpo.dayon.common.log.Log;

public abstract class SeriousErrorHandler {

	@Nullable
	private static JFrame frame;

	/**
	 * Displays a self closing translated warning message
	 */
	public static void warn(final String message) {

		if (frame != null) {
			final JLabel label = new JLabel();
			int timerDelay = 1000;
			new Timer(timerDelay, new ActionListener() {
				int timeLeft = 4;

				@Override
				public void actionPerformed(ActionEvent e) {
					if (timeLeft > 0) {
						StringBuilder sb = new StringBuilder("<html>");
						sb.append(Babylon.translate("serious.error.msg1")).append("<br/>");
						sb.append(Babylon.translate("serious.error.msg2", message)).append("<br/>");
						sb.append(Babylon.translate("serious.error.msg3", message)).append("</html>");
						label.setText(sb.toString());
						--timeLeft;
					} else {
						((Timer) e.getSource()).stop();
						Window win = SwingUtilities.getWindowAncestor(label);
						win.setVisible(false);
					}
				}
			}) {
				private static final long serialVersionUID = -3451080808563481433L;

				{
					setInitialDelay(0);
				}
			}.start();

			JOptionPane.showMessageDialog(frame, label, Babylon.translate("serious.error"), JOptionPane.WARNING_MESSAGE);

		} else {
			Log.error("Unable to display error message " + message);
		}

	}

	public static void attachFrame(JFrame frame) {
		SeriousErrorHandler.frame = frame;
	}
}
