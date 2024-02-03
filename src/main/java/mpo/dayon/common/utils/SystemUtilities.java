package mpo.dayon.common.utils;

import javax.net.ssl.HttpsURLConnection;
import javax.swing.*;
import javax.swing.plaf.metal.MetalLookAndFeel;
import java.io.*;
import java.math.BigInteger;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.lang.System.getProperty;
import static java.nio.charset.StandardCharsets.UTF_8;

public final class SystemUtilities {

    public static final String JAVA_CLASS_PATH = "java.class.path";
    public static final String FLATPAK_BROWSER = "/app/bin/dayon.browser";
    private static final String JAVA_VENDOR = "java.vendor";
    public static final String DEFAULT_TOKEN_SERVER_URL = "https://fensterkitt.ch/dayon/";

    private SystemUtilities() {
    }

    public static URI getQuickStartURI(String quickstartPage,String section) {
        return URI.create(format("http://retgal.github.io/Dayon/%s#%s-setup", quickstartPage, section));
    }

    private static File getOrCreateTransferDir() throws IOException {
        final File transferDir = new File(getProperty("dayon.home"), ".transfer");
        if (transferDir.exists()) {
            cleanDir(transferDir);
        } else if (!transferDir.mkdir()) {
            throw new IOException(format("Error creating %s", transferDir.getName()));
        }
        return transferDir;
    }

    public static String getTempDir() throws IOException {
        return isSnapped() ? getOrCreateTransferDir().getPath() : getProperty("java.io.tmpdir");
    }

    public static String getJarDir() {
        try {
            return Paths.get(SystemUtilities.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent().toString();
        } catch (URISyntaxException e) {
            return "";
        }
    }

    private static void cleanDir(File folder) throws IOException {
        try (Stream<Path> walk = Files.walk(folder.toPath())) {
            walk.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .filter(item -> !folder.getPath().equals(item.getPath()))
                    .forEach(File::delete);
        }
    }

    public static List<String> getSystemProperties() {
        final List<String> props = new ArrayList<>();
        final List<String> propNames = System.getProperties().keySet().stream().map(Object::toString).collect(Collectors.toList());
        final int size = propNames.stream().max(Comparator.comparing(String::length)).orElse("").length();
        final String format = "%" + size + "." + size + "s [%s]";

        propNames.stream().sorted().forEach(propName -> {
            String propValue = getProperty(propName);
            // I want to display the actual content of the line separator...
            if (propName.equals("line.separator")) {
                StringBuilder hex = new StringBuilder();
                for (int idx = 0; idx < propValue.length(); idx++) {
                    final int cc = propValue.charAt(idx);
                    hex.append("\\").append(cc);
                }
                propValue = hex.toString();
            }
            props.add(format(format, propName, propValue));
        });
        return props;
    }

    public static String getSystemPropertiesEx() {
        return getSystemProperties().stream().map(line -> line + System.lineSeparator()).collect(Collectors.joining());
    }

    public static String getRamInfo() {
        final double freeMG = Runtime.getRuntime().freeMemory();
        final double totalMG = Runtime.getRuntime().totalMemory();
        return format("%s of %s", UnitUtilities.toByteSize(totalMG - freeMG, false), UnitUtilities.toByteSize(totalMG, false));
    }

    public static void safeClose(Closeable... closeables) {
        Arrays.stream(closeables).filter(Objects::nonNull).forEachOrdered(open -> {
            try {
                open.close();
            } catch (IOException ignored) {
                // ignored
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
        return getProperty(JAVA_CLASS_PATH).startsWith("/snap/");
    }

    public static boolean isFlat() {
        return getProperty(JAVA_VENDOR).toLowerCase().startsWith("flat");
    }

    public static String getBuildNumber() {
        if (isSnapped()) {
            String classPath = getProperty(JAVA_CLASS_PATH);
            return classPath.substring(classPath.indexOf("dayon") + 6, classPath.lastIndexOf("/jar"));
        }
        return "";
    }

    /**
     * Computes the absolute path to dayon.browser
     */
    public static String getSnapBrowserCommand() {
        String cp = getProperty(JAVA_CLASS_PATH);
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
        if (serverName == null || !serverName.contains(".")) {
            return false;
        }
        try {
            return InetAddress.getByName(serverName) instanceof Inet4Address;
        } catch (UnknownHostException e) {
            return false;
        }
    }

    private static boolean isValidIpV6(String serverName) {
        try {
            return InetAddress.getByName(serverName) instanceof Inet6Address;
        } catch (UnknownHostException e) {
            return false;
        }
    }

    @java.lang.SuppressWarnings("squid:S5998") // matcher input is max 256 chars long
    private static boolean isValidHostname(String serverName) {
        return !isLookingLikeAnIpV4(serverName) && serverName.length() < 256 &&
                serverName.matches("^([a-zA-Z\\d][a-zA-Z\\d\\-]{0,61}[a-zA-Z\\d]\\.)*[a-zA-Z]{2,}$");
    }

    private static boolean isLookingLikeAnIpV4(String serverName) {
        return Arrays.stream(serverName.split("\\.")).allMatch(e -> e.matches("(\\d{1,3})"));
    }

    public static boolean isValidToken(String token) throws NoSuchAlgorithmException {
        return (!token.isEmpty() && token.substring(token.length()-1).equals(checksum(token.substring(0, token.length()-1))));
    }

    static String checksum(String input) throws NoSuchAlgorithmException {
        MessageDigest objSHA = MessageDigest.getInstance("SHA-1");
        byte[] bytSHA = objSHA != null ? objSHA.digest(input.getBytes()) : new byte[0];
        BigInteger intNumber = new BigInteger(1, bytSHA);
        String hash = intNumber.toString(16);
        return hash.substring(hash.length()-1).toUpperCase();
    }

    public static String resolveToken(String tokenServerUrl, String token) throws IOException {
        URL url = new URL(format(tokenServerUrl, token));
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        conn.setInstanceFollowRedirects(false);
        conn.setReadTimeout(3000);
        conn.disconnect();
        return new BufferedReader(new InputStreamReader(conn.getInputStream(), UTF_8)).readLine().trim();
    }
}
