package mpo.dayon.common;

import mpo.dayon.common.log.Log;
import mpo.dayon.common.utils.SystemUtilities;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public interface Runner {
    static void logAppInfo(String appName) {
        // System.setProperty("dayon.debug", "on");
        SystemUtilities.setApplicationName(appName);
        Log.info("============================================================================================");
        for (String line : SystemUtilities.getSystemProperties()) {
            Log.info(line);
        }
        Log.info("============================================================================================");
    }

    @NotNull
    static Map<String, String> extractProgramArgs(String[] args) {
        return Arrays.stream(args)
                .map(arg -> arg
                .replace(",", "")
                .trim()
                .split("="))
                .filter(pair -> pair.length == 2)
                .collect(Collectors.toMap(pair -> pair[0], pair -> pair[1], (a, b) -> b));
    }

    static void overrideLocale(String arg) {
        final String[] supported = {"de", "en", "es", "fr", "ru"};
        if (arg != null && Arrays.stream(supported).anyMatch(e -> e.equalsIgnoreCase(arg))) {
            Locale.setDefault(new Locale(arg));
        }
    }
}
