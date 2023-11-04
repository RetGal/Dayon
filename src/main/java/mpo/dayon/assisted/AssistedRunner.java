package mpo.dayon.assisted;

import mpo.dayon.assisted.gui.Assisted;
import mpo.dayon.common.Runner;
import mpo.dayon.common.log.Log;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.String.format;
import static mpo.dayon.common.utils.SystemUtilities.getJarDir;

public class AssistedRunner {

    public static void main(String[] args) {
        Runner.main(args);
    }

    public static void launchAssisted(String assistantHost, String assistantPort) {
        final Assisted assisted = new Assisted();
        assisted.setup();
        // cli args have precedence
        if (assistantHost == null || assistantPort == null) {
            final Map<String, String> config = readPresetFile();
            assisted.start(config.get("host"), config.get("port"), isAutoConnect(config));
        } else {
            assisted.start(assistantHost, assistantPort, true);
        }
    }

    private static Map<String, String> readPresetFile() {
        final List<String> paths = Arrays.asList(System.getProperty("dayon.home"), System.getProperty("user.home"), getJarDir());
        final String fileName = "assisted.yaml";
        return paths.stream().map(path -> new File(path, fileName)).filter(AssistedRunner::isReadable).map(AssistedRunner::parseFileContent).filter(content -> !content.isEmpty()).findFirst().orElse(Collections.emptyMap());
    }

    private static boolean isReadable(File presetFile) {
        return presetFile.exists() && presetFile.isFile() && presetFile.canRead();
    }

    private static Map<String, String> parseFileContent(File presetFile) {
        try (Stream<String> lines = Files.lines(presetFile.toPath())) {
            final Map<String, String> content = lines.map(line -> line.split(":")).filter(s -> s.length > 1).collect(Collectors.toMap(s -> s[0].trim(), s -> s[1].trim()));
            if (content.containsKey("host") && content.containsKey("port")) {
                Log.info(format("Using connection settings from [%s]", presetFile.getPath()));
                return content;
            }
        } catch (IOException e) {
            Log.warn(e.getMessage());
        }
        return Collections.emptyMap();
    }

    private static boolean isAutoConnect(Map<String, String> config) {
        return !config.containsKey("autoConnect") || !config.get("autoConnect").equalsIgnoreCase("false");
    }
}
