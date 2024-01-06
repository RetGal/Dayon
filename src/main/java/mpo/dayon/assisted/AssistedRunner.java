package mpo.dayon.assisted;

import mpo.dayon.assisted.gui.Assisted;
import mpo.dayon.common.Runner;

import java.util.Map;

import static mpo.dayon.common.Runner.isAutoConnect;
import static mpo.dayon.common.Runner.readPresetFile;

public class AssistedRunner {

    public static void main(String[] args) {
        Runner.main(args);
    }

    public static void launchAssisted(String assistantHost, String assistantPort) {
        // cli args have precedence
        if (assistantHost == null || assistantPort == null) {
            final Map<String, String> config = readPresetFile("assisted.yaml");
            final Assisted assisted = new Assisted(config.get("tokenServerUrl"));
            assisted.start(config.get("host"), config.get("port"), isAutoConnect(config));
        } else {
            final Assisted assisted = new Assisted(null);
            assisted.start(assistantHost, assistantPort, true);
        }
    }
}
