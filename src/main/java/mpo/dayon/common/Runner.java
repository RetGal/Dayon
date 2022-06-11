package mpo.dayon.common;

import mpo.dayon.common.log.Log;
import mpo.dayon.common.utils.SystemUtilities;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public interface Runner {

    static void logAppInfo(String appName) {
        System.setProperty("dayon.application.name", appName);
        Log.info("============================================================================================");
        for (String line : SystemUtilities.getSystemProperties()) {
            Log.info(line);
        }
        Log.info("============================================================================================");
    }

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
        final String[] supported = {"de", "en", "es", "fr", "it", "ru", "tr", "zh"};
        if (arg != null && Arrays.stream(supported).anyMatch(e -> e.equalsIgnoreCase(arg))) {
            Locale.setDefault(new Locale(arg));
        }
    }

    static void setDebug(String[] args) {
        if (Arrays.stream(args).anyMatch(a -> a.equalsIgnoreCase("debug"))) {
            System.setProperty("dayon.debug", "on");
        }
    }

    static void disableDynamicScale() {
        System.setProperty("sun.java2d.uiScale", "1");
    }
}
