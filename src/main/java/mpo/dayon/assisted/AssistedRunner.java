package mpo.dayon.assisted;

import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.Win32Exception;
import mpo.dayon.assisted.gui.Assisted;
import mpo.dayon.common.Runner;
import mpo.dayon.common.log.Log;

import java.io.File;
import java.util.Map;

import static mpo.dayon.common.Runner.isAutoConnect;
import static mpo.dayon.common.Runner.readPresetFile;

import static com.sun.jna.platform.win32.WinReg.HKEY_LOCAL_MACHINE;

public class AssistedRunner {

    public static void main(String[] args) {
        fixUacBehaviour();
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

    private static void fixUacBehaviour() {
        if (File.separatorChar == '/') {
            return;
        }
        final int off = 0x00000000;
        final int on = 0x00000001;
        final int secureDesktop = Advapi32Util.registryGetIntValue
                (HKEY_LOCAL_MACHINE,
                        "Software\\Microsoft\\Windows\\CurrentVersion\\Policies\\System",
                        "PromptOnSecureDesktop");
        if (off != secureDesktop) {
            try {
                Advapi32Util.registrySetIntValue
                        (HKEY_LOCAL_MACHINE,
                                "Software\\Microsoft\\Windows\\CurrentVersion\\Policies\\System", "PromptOnSecureDesktop", off);
                Advapi32Util.registrySetIntValue
                        (HKEY_LOCAL_MACHINE,
                                "Software\\Microsoft\\Windows\\CurrentVersion\\Policies\\System", "EnableLUA", on);
            } catch(Win32Exception e) {
                Log.warn("Could not fix UAC behaviour, UAC dialogs will not be visible");
                Log.warn("Rerun the assisted with admin rights to fix this");
            }
        }
    }
}