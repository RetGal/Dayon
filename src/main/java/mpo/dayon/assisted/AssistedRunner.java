package mpo.dayon.assisted;

import mpo.dayon.assisted.gui.Assisted;
import mpo.dayon.common.Runner;
import mpo.dayon.common.error.FatalErrorHandler;

import javax.swing.SwingUtilities;
import java.util.Map;

class AssistedRunner implements Runner {
    public static void main(String[] args) {
        try {
            Map<String, String> programArgs = Runner.extractProgramArgs(args);
            Runner.overrideLocale(programArgs.get("lang"));
            Runner.logAppInfo("dayon_assisted");
            SwingUtilities.invokeLater(() -> launchAssisted(programArgs.get("ah"), programArgs.get("ap")));
        } catch (Exception ex) {
            FatalErrorHandler.bye("The assisted is dead!", ex);
        }
    }

    private static void launchAssisted(String assistantHost, String assistantPort) {
        final Assisted assisted = new Assisted();
        assisted.configure();
        assisted.start(assistantHost, assistantPort);
    }
}
