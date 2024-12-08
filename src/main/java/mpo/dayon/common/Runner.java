package mpo.dayon.common;

import mpo.dayon.assistant.AssistantRunner;
import mpo.dayon.assisted.AssistedRunner;
import mpo.dayon.common.error.FatalErrorHandler;
import mpo.dayon.common.log.Log;
import mpo.dayon.common.utils.Language;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.String.format;
import static mpo.dayon.common.utils.SystemUtilities.*;

public interface Runner {

    static void main(String[] args) {
        Runner.setDebug(args);
        final File appHomeDir = Runner.getOrCreateAppHomeDir();
        Map<String, String> programArgs = Runner.extractProgramArgs(args);
        String language = Runner.overrideLocale(programArgs.get("lang"));
        SwingUtilities.invokeLater(() -> {
            Runner.logAppInfo(hasAssistant(args) ? "dayon_assistant" : "dayon_assisted");
            try {
                if (hasAssistant(args)) {
                    AssistantRunner.launchAssistant(language);
                } else {
                    AssistedRunner.launchAssisted(programArgs.get("ah"), programArgs.get("ap"));
                }
            } catch (Exception ex) {
                FatalErrorHandler.bye(hasAssistant(args) ? "The assistant is dead!" : "The assisted is dead!", ex);
            }
        });
        new Thread(() -> prepareKeystore(appHomeDir)).start();
    }

    static void logAppInfo(String appName) {
        System.setProperty("dayon.application.name", appName);
        Log.info("============================================================================================");
        getSystemProperties().forEach(Log::info);
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

    static String overrideLocale(String arg) {
        if (arg != null && Arrays.stream(Language.values()).map(Language::getShortName).anyMatch(e -> e.equalsIgnoreCase(arg))) {
            Locale.setDefault(Locale.forLanguageTag(arg));
            return arg;
        }
        return null;
    }

    static void setDebug(String[] args) {
        if (Arrays.stream(args).anyMatch(a -> a.equalsIgnoreCase("debug"))) {
            System.setProperty("dayon.debug", "true");
        }
    }

    static boolean hasAssistant(String[] args) {
        return Arrays.stream(args).anyMatch(a -> a.equalsIgnoreCase("assistant"));
    }

    static File getOrCreateAppHomeDir() {
        final String homeDir = System.getProperty("user.home"); // *.log4j.xml are using that one (!)
        if (homeDir == null) {
            Log.warn("Home directory [user.home] is null!");
            return null;
        }

        final File home = new File(homeDir);
        if (!home.isDirectory()) {
            Log.warn(format("Home directory [%s] is not a directory!", homeDir));
            return null;
        }

        File appHomeDir;
        if (isSnapped()) {
            final String classPath = System.getProperty(JAVA_CLASS_PATH);
            final String userDataDir = format("%s%s", homeDir, classPath.substring(0, classPath.indexOf("/jar/dayon.jar")));
            appHomeDir = new File(userDataDir, ".dayon");
        } else {
            appHomeDir = new File(home, ".dayon");
        }

        if (!appHomeDir.exists() && !appHomeDir.mkdir()) {
            Log.warn(format("Could not create the application directory [%s]!", appHomeDir.getAbsolutePath()));
            return home;
        }
        System.setProperty("dayon.home", appHomeDir.getAbsolutePath());
        return appHomeDir;
    }

    static void prepareKeystore(File appHomeDir) {
        if (appHomeDir == null) {
            Log.error("Skipping keystore creation, application directory is missing!");
            return;
        }
        Path keystore = Paths.get(format("%s%skeystore.jks", appHomeDir.getAbsolutePath(), File.separator));
        if (!Files.exists(keystore)) {
            Log.info(format("Creating new keystore [%s]", keystore));
            String keytool = Paths.get(format("%s%sbin%skeytool", System.getProperty("java.home"), File.separator, File.separator)).toString();
            ProcessBuilder builder = new ProcessBuilder(keytool, "-genkeypair", "-dname", "cn=Dayon!, ou=Dayon!, o=Dayon!, c=Dayon!, l=Dayon!", "-keyalg", "RSA", "-keysize", "4096", "-alias", "genkey",  "-validity", "3210", "-keystore", keystore.toString(), "-storepass", "spasspass");
            try {
                builder.directory(appHomeDir).start();
            } catch (IOException e) {
                Log.error(format("Failed to create keystore [%s]", keystore), e);
            }
        }
    }

    static Map<String, String> readPresetFile(String presetFile) {
        final List<String> paths = Arrays.asList(System.getProperty("dayon.home"), System.getProperty("user.home"), getJarDir());
        return paths.stream().map(path -> new File(path, presetFile)).filter(Runner::isReadableFile).map(Runner::parsePresetFileContent).filter(content -> !content.isEmpty()).findFirst().orElse(Collections.emptyMap());
    }

    static boolean isReadableFile(File presetFile) {
        return presetFile.isFile() && presetFile.canRead();
    }

    static Map<String, String> parsePresetFileContent(File presetFile) {
        try (Stream<String> lines = Files.lines(presetFile.toPath())) {
            final Map<String, String> content = lines.map(line -> line.split(":")).filter(s -> s.length > 1).collect(Collectors.toMap(s -> s[0].trim(), Runner::parseValue));
            if ((content.containsKey("host") && content.containsKey("port")) || content.containsKey("tokenServerUrl")) {
                Log.info(format("Using connection settings from [%s]", presetFile.getPath()));
                return content;
            }
        } catch (IOException e) {
            Log.warn(e.getMessage());
        }
        return Collections.emptyMap();
    }

    static String parseValue(String[] s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < s.length; i++) {
            sb.append(s[i].trim().replaceAll("(^[\"'])|([\"']$)", "")).append(":");
        }
        return sb.deleteCharAt(sb.length()-1).toString();
    }

    static boolean isAutoConnect(Map<String, String> config) {
        return !config.containsKey("autoConnect") || !config.get("autoConnect").equalsIgnoreCase("false");
    }
}
