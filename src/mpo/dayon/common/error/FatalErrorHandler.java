package mpo.dayon.common.error;

import mpo.dayon.common.babylon.Babylon;
import mpo.dayon.common.log.Log;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class FatalErrorHandler
{
    @Nullable
    private static JFrame frame;

    public static void bye(String message, Throwable error)
    {
        Log.fatal(message, error);
        Log.fatal("Bye!");

        if (frame != null)
        {
            String info = error.getMessage();

            if (info == null)
            {
                info = Babylon.translate("fatal.error.msg3");
            }

            JOptionPane.showMessageDialog(frame,
                                          Babylon.translate("fatal.error.msg1")
                                          + "\n" + Babylon.translate("fatal.error.msg2", info),
                                          Babylon.translate("fatal.error"),
                                          JOptionPane.ERROR_MESSAGE);
        }

        System.exit(-1);
    }

    public static void attachFrame(JFrame frame)
    {
        FatalErrorHandler.frame = frame;
    }
}
