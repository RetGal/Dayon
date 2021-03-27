package mpo.dayon.common.utils;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidParameterException;
import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.UIManager;
import javax.swing.plaf.metal.MetalLookAndFeel;

import mpo.dayon.common.babylon.Babylon;
import mpo.dayon.common.gui.common.FrameType;
import mpo.dayon.common.log.Log;

import static java.lang.Math.abs;

public abstract class SystemUtilities {

    private SystemUtilities() {
    }

    public static URI getQuickStartURI(FrameType frameType) throws URISyntaxException {
        return new URI(String.format("http://retgal.github.io/Dayon/%s#%s-setup", Babylon.translate("quickstart.html"), frameType.getPrefix()));
    }

    private static synchronized File getOrCreateAppDir() {
        final String homeDir = System.getProperty("user.home"); // *.log4j.xml are using that one (!)
        if (homeDir == null) {
            Log.warn("Home directory [user.home] is null!");
            return null;
        }

        final File home = new File(homeDir);
        if (!home.isDirectory()) {
            Log.warn("Home directory [" + homeDir + "] is not a directory!");
            return null;
        }

        final File appDir = new File(home, ".dayon");
        if (!appDir.exists() && !appDir.mkdir()) {
            Log.warn("Could not create the application directory [" + appDir.getAbsolutePath() + "]!");
            return home;
        }
        return appDir;
    }

    public static File getOrCreateAppFile(String name) {
        final File file = new File(getOrCreateAppDir(), name);
        if (file.exists() && file.isDirectory()) {
            Log.warn("Could not create the application file [" + name + "]!");
            return null;
        }
        return file;
    }

    private static File getOrCreateTransferDir() {
        final File transferDir = new File(getOrCreateAppDir(), ".transfer");
        if (transferDir.exists()) {
            cleanDir(transferDir);
        } else if (!transferDir.mkdir()) {
            Log.warn("Could not create the transfer directory [" + transferDir + "]!");
        }
        return transferDir;
    }

    public static String getTempDir() {
        return isSnapped() ? getOrCreateTransferDir().getPath() : System.getProperty("java.io.tmpdir");
    }

    private static void cleanDir(File folder) {
        try (Stream<Path> walk = Files.walk(folder.toPath())) {
            walk.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .filter(item -> !folder.getPath().equals(item.getPath()))
                    .forEach(File::delete);
        } catch (IOException e) {
            Log.warn(String.format("Could not clean %s", folder));
        }
    }

    public static String getApplicationName() {
        try {
            return SystemUtilities.getStringProperty(null, "dayon.application.name");
        } catch (InvalidParameterException ex) {
            throw new InvalidParameterException("Missing application name!");
        }
    }

    public static void setApplicationName(String name) {
        System.setProperty("dayon.application.name", name);
    }

    private static String getStringProperty(Properties props, String name) {
        final String value = getStringProperty(props, name, null);
        if (value == null) {
            throw new InvalidParameterException(MessageFormat.format("Missing value for property {0}!", name));
        }
        return value;
    }

    public static String getStringProperty(Properties props, String name, String defaultValue) {
        if (props == null) {
            return System.getProperty(name);
        }
        return props.getProperty(name, defaultValue);
    }

    public static int getIntProperty(Properties props, String name, int defaultValue) {
        final String prop = getStringProperty(props, name, null);
        if (prop == null) {
            return defaultValue;
        }
        return abs(Integer.parseInt(prop));
    }

    public static boolean getBooleanProperty(Properties props, String name, boolean defaultValue) {
        final String prop = getStringProperty(props, name, null);
        if (prop == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(prop);
    }

    public static double getDoubleProperty(Properties props, String name, double defaultValue) {
        final String prop = getStringProperty(props, name, null);
        if (prop == null) {
            return defaultValue;
        }
        return abs(Double.parseDouble(prop));
    }

    public static <T extends Enum<T>> T getEnumProperty(Properties props, String name, T defaultValue, T[] enums) {
        final String prop = getStringProperty(props, name, null);
        if (prop == null) {
            return defaultValue;
        }
        final int ordinal = Integer.parseInt(prop);
        return Arrays.stream(enums).filter(anEnum -> ordinal == anEnum.ordinal()).findFirst().orElse(defaultValue);
    }

    public static List<String> getSystemProperties() {
        final List<String> props = new ArrayList<>();
        final List<String> propNames = System.getProperties().keySet().stream().map(Object::toString).collect(Collectors.toList());
        final int size = propNames.stream().max(Comparator.comparing(String::length)).orElse("").length();
        final String format = "%" + size + "." + size + "s [%s]";

        Collections.sort(propNames);

        for (String propName : propNames) {
            String propValue = System.getProperty(propName);
            // I want to display the actual content of the line separator...
            if (propName.equals("line.separator")) {
                StringBuilder hex = new StringBuilder();
                for (int idx = 0; idx < propValue.length(); idx++) {
                    final int cc = propValue.charAt(idx);
                    hex.append("\\").append(cc);
                }
                propValue = hex.toString();
            }
            props.add(String.format(format, propName, propValue));
        }
        return props;
    }

    public static String getSystemPropertiesEx() {
        return getSystemProperties().stream().map(line -> line + System.getProperty("line.separator")).collect(Collectors.joining());
    }

    public static String getRamInfo() {
        final double freeMG = Runtime.getRuntime().freeMemory();
        final double totalMG = Runtime.getRuntime().totalMemory();
        return UnitUtilities.toByteSize(totalMG - freeMG, false) + " of " + UnitUtilities.toByteSize(totalMG, false);
    }

    public static void safeClose(Closeable... closeables) {
        Arrays.stream(closeables).filter(Objects::nonNull).forEachOrdered(open -> {
            Log.debug(open.getClass().getSimpleName() + " closing");
            try {
                open.close();
            } catch (IOException ignored) {
                Log.debug(open.getClass().getSimpleName() + " closing failed");
            }
        });
    }

    public static Thread safeInterrupt(Thread thread) {
        if (thread != null) {
            thread.interrupt();
        }
        return null;
    }

    public static String getDefaultLookAndFeel() {
        for (UIManager.LookAndFeelInfo lookAndFeelInfo : UIManager.getInstalledLookAndFeels()) {
            if (lookAndFeelInfo.getName().equals("Nimbus")) {
                return lookAndFeelInfo.getClassName();
            }
        }
        return MetalLookAndFeel.class.getName();
    }

    public static boolean isSnapped() {
        return System.getProperty("java.class.path").startsWith("/snap/");
    }

    /**
     * Computes the absolute path to dayon.browser
     */
    public static String getSnapBrowserCommand() {
        String cp = System.getProperty("java.class.path");
        return cp.substring(0, cp.indexOf("jar")) + "bin/dayon.browser";
    }

    public static boolean isValidPortNumber(String portNumber) {
        try {
            int port = Integer.parseInt(portNumber);
            if (port < 1 || port > 65535) {
                return false;
            }
        } catch (NumberFormatException ex) {
            return false;
        }
        return true;
    }

    public static boolean isValidIpAddressOrHostName(String serverName) {
        return serverName != null && (isValidIpV4(serverName) || isValidIpV6(serverName) || isValidHostname(serverName));
    }

    private static boolean isValidIpV4(String serverName) {
        try {
            InetAddress inetAddress = InetAddress.getByName(serverName);
            return inetAddress instanceof Inet4Address;
        } catch (UnknownHostException e) {
            return false;
        }
    }

    private static boolean isValidIpV6(String serverName) {
        try {
            InetAddress inetAddress = InetAddress.getByName(serverName);
            return inetAddress instanceof Inet6Address;
        } catch (UnknownHostException e) {
            return false;
        }
    }

    @java.lang.SuppressWarnings("squid:S5998") // matcher input is max 256 chars long
    private static boolean isValidHostname(String serverName) {
        return !isLookingLikeAnIpV4(serverName) && serverName.length() < 256 &&
                serverName.matches("^([a-zA-Z0-9][a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])(\\.([a-zA-Z0-9][a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9]))*$");
    }

    private static boolean isLookingLikeAnIpV4(String serverName) {
        return Arrays.stream(serverName.split("\\.")).allMatch(e -> e.matches("([0-9]{1,3})"));
    }

}
