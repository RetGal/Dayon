package mpo.dayon.common.utils;

import mpo.dayon.common.gui.common.FrameType;
import mpo.dayon.common.log.Log;

import javax.net.ssl.HttpsURLConnection;
import javax.swing.*;
import javax.swing.plaf.metal.MetalLookAndFeel;
import java.io.*;
import java.math.BigInteger;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidParameterException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.Math.abs;
import static mpo.dayon.common.babylon.Babylon.translate;

public final class SystemUtilities {

    public static final String JAVA_CLASS_PATH = "java.class.path";
    public static final String JAVA_VENDOR = "java.vendor";
    public static final String TOKEN_SERVER_URL = "https://fensterkitt.ch/dayon/?token=%s";

    private SystemUtilities() {
    }

    public static URI getQuickStartURI(FrameType frameType) {
        return URI.create(String.format("http://retgal.github.io/Dayon/%s#%s-setup", translate("quickstart.html"), frameType.getPrefix()));
    }

    public static synchronized File getOrCreateAppDir() {
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

        File appDir;
        if (isSnapped()) {
            final String classPath = System.getProperty(JAVA_CLASS_PATH);
            final String userDataDir = String.format("%s%s", homeDir, classPath.substring(0, classPath.indexOf("/jar/dayon.jar")));
            appDir = new File(userDataDir, ".dayon");
        } else {
            appDir = new File(home, ".dayon");
        }

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

    public static String getJarDir() {
        String jarPath = "";
        try {
            jarPath = Paths.get(SystemUtilities.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent().toString();
        } catch (URISyntaxException e) {
            Log.warn(e.getMessage());
        }
        return jarPath;
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
        return System.getProperty(JAVA_CLASS_PATH).startsWith("/snap/");
    }

    public static boolean isFlat() {
        return System.getProperty(JAVA_VENDOR).toLowerCase().startsWith("flat");
    }

    public static String getBuildNumber() {
        if (isSnapped()) {
            String classPath = System.getProperty(JAVA_CLASS_PATH);
            return classPath.substring(classPath.indexOf("dayon") + 6, classPath.lastIndexOf("/jar"));
        }
        return "";
    }

    /**
     * Computes the absolute path to dayon.browser
     */
    public static String getSnapBrowserCommand() {
        String cp = System.getProperty(JAVA_CLASS_PATH);
        return cp.substring(0, cp.indexOf("jar")) + "bin/dayon.browser";
    }

    public static String getFlatpakBrowserCommand() {
        return "/app/bin/dayon.browser";
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
        if (serverName == null || !serverName.contains(".")) {
            return false;
        }
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
                serverName.matches("^([a-zA-Z\\d][a-zA-Z\\d\\-]{0,61}[a-zA-Z\\d])(\\.([a-zA-Z\\d][a-zA-Z\\d\\-]{0,61}[a-zA-Z\\d]))*$");
    }

    private static boolean isLookingLikeAnIpV4(String serverName) {
        return Arrays.stream(serverName.split("\\.")).allMatch(e -> e.matches("(\\d{1,3})"));
    }

    public static boolean isValidToken(String token) {
        try {
            if (token.trim().isEmpty() || !token.substring(token.length()-1).equals(checksum(token.substring(0, token.length()-1)))) {
                return false;
            }
        } catch (NumberFormatException ex) {
            return false;
        }
        return true;
    }

    public static String checksum(String input) {
        MessageDigest objSHA = null;
        try {
            objSHA = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        byte[] bytSHA = objSHA.digest(input.getBytes());
        BigInteger intNumber = new BigInteger(1, bytSHA);
        String hash = intNumber.toString(16);
        return hash.substring(hash.length()-1).toUpperCase();
    }

    public static String resolveToken(String token) {
        HttpsURLConnection conn = null;
        try {
            URL url = new URL(String.format(TOKEN_SERVER_URL, token));
            conn = (HttpsURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setReadTimeout(3000);
            return new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))
                    .readLine().trim();
        } catch (IOException e) {
            Log.error("IOException", e);
        } finally {
            Objects.requireNonNull(conn).disconnect();
        }
        return null;
    }
}
