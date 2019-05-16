package mpo.dayon.common.utils;

import java.io.*;
import java.net.*;
import java.security.InvalidParameterException;
import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Collectors;

import javax.swing.UIManager;
import javax.swing.plaf.metal.MetalLookAndFeel;

import org.jetbrains.annotations.Nullable;

import mpo.dayon.common.babylon.Babylon;
import mpo.dayon.common.log.Log;

public abstract class SystemUtilities {

    private SystemUtilities() {
    }

    @Nullable
    private static File getInstallRoot() {
        String path = null;
        try {
            path = new URI(SystemUtilities.class.getProtectionDomain().getCodeSource().getLocation().getPath()).getPath();
        } catch (URISyntaxException e) {
            Log.warn("Failed to retrieve InstallRoot", e);
        }
        int pos = 0;
        if (Objects.requireNonNull(path).contains("/lib/dayon.jar")) {
            pos = path.indexOf("/lib");
        } else if (path.contains("/target/classes")) {
            pos = path.indexOf("/classes");
        } else if (path.contains("/out/idea/")) {
            pos = path.indexOf("/out");
        }
        if (pos != 0) {
            return new File(path.substring(0, pos));
        }
        return null;
    }

    @Nullable
    public static File getDayonJarPath() {
        final File rootPath = getInstallRoot();
        if (rootPath == null) {
            return null;
        }

        File path = new File(rootPath, "dayon.jar");
        if (path.exists() && path.isFile()) {
            return rootPath;
        }

        path = new File(rootPath, "lib");
        if (path.exists() && path.isDirectory()) {
            return path;
        }
        return null;
    }

    @Nullable
    public static URI getLocalIndexHtml() {
        @Nullable final File rootPath = getInstallRoot();
        if (rootPath != null) {
            // Anchor not supported : #assistant-setup
            File quickStart = new File(rootPath, "doc/html/" + Babylon.translate("quickstart.html"));
            // Must be running inside an IDE
            if (!quickStart.isFile() && rootPath.getAbsolutePath().endsWith("/target")) {
                quickStart = new File(rootPath.getParent(), "docs/" + Babylon.translate("quickstart.html"));
                return quickStart.toURI();
            }
        }
        return null;
    }

    @Nullable
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
            return null;
        }
        return appDir;
    }

    @Nullable
    public static File getOrCreateAppDirectory(String name) {
        final File home = getOrCreateAppDir();
        if (home == null) {
            Log.warn("Could not create the application directory (1) [" + name + "]!");
            return null;
        }

        final File dir = new File(home, name);
        if (dir.exists() && !dir.isDirectory()) {
            Log.warn("Could not create the application directory (2) [" + name + "]!");
            return null;
        }

        if (!dir.exists() && !dir.mkdir()) {
            Log.warn("Could not create the application directory (3) [" + name + "]!");
            return null;
        }
        return dir;
    }

    @Nullable
    public static File getOrCreateAppFile(String name) {
        final File home = getOrCreateAppDir();
        if (home == null) {
            Log.warn("Could not create the application file (1) [" + name + "]!");
            return null;
        }

        final File file = new File(home, name);
        if (file.exists() && file.isDirectory()) {
            Log.warn("Could not create the application file (2) [" + name + "]!");
            return null;
        }
        return file;
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

    private static String getStringProperty(@Nullable Properties props, String name) {
        final String value = getStringProperty(props, name, null);
        if (value == null) {
            throw new InvalidParameterException(MessageFormat.format("Missing value for property {0}!", name));
        }
        return value;
    }

    public static String getStringProperty(@Nullable Properties props, String name, String defaultValue) {
        if (props == null) {
            final String prop = System.getProperty(name);

            if (prop == null) {
                return System.getProperty("jnlp." + name, defaultValue);
            }
            return prop;
        }
        return props.getProperty(name, defaultValue);
    }

    public static int getIntProperty(@Nullable Properties props, String name, int defaultValue) {
        final String prop = getStringProperty(props, name, null);
        if (prop == null) {
            return defaultValue;
        }
        return Integer.valueOf(prop);
    }

    public static boolean getBooleanProperty(@Nullable Properties props, String name, boolean defaultValue) {
        final String prop = getStringProperty(props, name, null);
        if (prop == null) {
            return defaultValue;
        }
        return Boolean.valueOf(prop);
    }

    public static double getDoubleProperty(@Nullable Properties props, String name, double defaultValue) {
        final String prop = getStringProperty(props, name, null);
        if (prop == null) {
            return defaultValue;
        }
        return Double.valueOf(prop);
    }

    public static <T extends Enum<T>> T getEnumProperty(@Nullable Properties props, String name, T defaultValue, T[] enums) {
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
        int size = propNames.stream().max(Comparator.comparing(String::length)).orElse("").length();

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
            props.add(String.format("%" + size + "." + size + "s [%s]", propName, propValue));
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

    public static void safeClose(@Nullable Closeable open) {
        if (open != null) {
            Log.debug(open.getClass().getSimpleName() + " closing");
            try {
                open.close();
            } catch (IOException ignored) {
                Log.debug(open.getClass().getSimpleName() + " closing failed");
            }
        }
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

    public static boolean isValidPortNumber(String portNumber) {
        try {
            Integer port = Integer.valueOf(portNumber);
            if (port > 65535) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException ex) {
            return false;
        }
        return true;
    }

    public static String formatIPv6(String serverName) {
        if (isValidIpV6(serverName)) {
            return '[' + serverName + ']';
        }
        return serverName;
    }

    public static boolean isValidIpAdressOrHostName(String serverName) {
        return isValidIpV4(serverName) || isValidIpV6(serverName) || isValidHostname(serverName);
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

    private static boolean isValidHostname(String serverName) {
        return serverName.matches("^(([a-zA-Z]|[a-zA-Z][a-zA-Z0-9\\-]*[a-zA-Z0-9])\\.)*([A-Za-z0-9]|[A-Za-z0-9][A-Za-z0-9\\-]*[A-Za-z0-9])$");
    }

}
