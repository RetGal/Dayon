package mpo.dayon.common.error;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import mpo.dayon.common.log.Log;

import static java.lang.String.format;
import static mpo.dayon.common.babylon.Babylon.translate;

public abstract class KeyboardErrorHandler {
    private static JFrame frame;

    private KeyboardErrorHandler() {
    }

    /**
     * Displays a self closing translated warning message
     */
    public static void warn(final String message) {

        if (frame != null) {
            final JLabel label = new JLabel();
            int timerDelay = 1000;
            ActionListener ac = new ActionListener() {
                int timeLeft = 4;

                @Override
                public void actionPerformed(ActionEvent e) {
                    if (timeLeft > 0) {
                        --timeLeft;
                    } else {
                        ((Timer) e.getSource()).stop();
                        if (SwingUtilities.getWindowAncestor(label) != null) {
                            SwingUtilities.getWindowAncestor(label).setVisible(false);
                        }
                    }
                }
            };

            Timer timer = new Timer(timerDelay, ac);
            timer.setInitialDelay(0);
            timer.start();

            String sb = format("<html>%s<br/>%s<br/>%s</html>", translate("keyboard.error.msg1"),
                    translate("keyboard.error.msg2", message), translate("keyboard.error.msg3", message));
            label.setText(sb);

            JOptionPane.showMessageDialog(frame, label, translate("keyboard.error"), JOptionPane.WARNING_MESSAGE);

        } else {
            Log.error("Unable to display error message " + message);
        }

    }

    public static void attachFrame(JFrame frame) {
        KeyboardErrorHandler.frame = frame;
    }
}
