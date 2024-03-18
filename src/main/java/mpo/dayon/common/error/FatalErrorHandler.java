package mpo.dayon.common.error;

import javax.swing.JFrame;
import javax.swing.JOptionPane;

import mpo.dayon.common.log.Log;

import java.util.Objects;

import static java.lang.String.format;
import static mpo.dayon.common.babylon.Babylon.translate;

public final class FatalErrorHandler {
    private static JFrame frame;

    private FatalErrorHandler() {
    }

    /**
     * Displays a translated error message and terminates
     */
    public static void bye(String message, Throwable error) {
        Log.fatal(message, error);
        Log.fatal("Bye!");

        if (frame != null) {
            String info = translate(Objects.requireNonNullElse(error.getMessage(), "fatal.error.msg3"));
            JOptionPane.showMessageDialog(frame, format("%s%n%s", translate("fatal.error.msg1"), translate("fatal.error.msg2", info)),
                    translate("fatal.error"), JOptionPane.ERROR_MESSAGE);
        }

        System.exit(-1);
    }

    public static void attachFrame(JFrame frame) {
        FatalErrorHandler.frame = frame;
    }
}
