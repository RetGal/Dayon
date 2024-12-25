package mpo.dayon.common.utils;

import javax.swing.*;
import javax.swing.plaf.metal.MetalLookAndFeel;
import java.io.*;
import java.math.BigInteger;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.lang.System.getProperty;
import static mpo.dayon.common.utils.UnitUtilities.toByteSize;

public final class SystemUtilities {

    public static final String JAVA_CLASS_PATH = "java.class.path";
    public static final String FLATPAK_BROWSER = "/app/bin/dayon.browser";
    private static final String JAVA_VENDOR = "java.vendor";
    private static final Pattern FQ_HOSTNAME_REGEX = Pattern.compile("^([a-zA-Z\\d][a-zA-Z\\d\\-]{0,61}[a-zA-Z\\d]\\.)*[a-zA-Z]{2,}$");
    private static final Pattern IPV4_REGEX = Pattern.compile("(\\d{1,3})");

    private SystemUtilities() {
    }

    public static URI getQuickStartURI(String quickstartPage, String section) {
        return URI.create(format("https://retgal.github.io/Dayon/%s#%s-setup", quickstartPage, section));
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
            final Path parent = Paths.get(SystemUtilities.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();
            return parent != null ? parent.toString() : "";
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
        final List<String> props = new ArrayList<>(64);
        final List<String> propNames = System.getProperties().keySet().stream().map(Object::toString).collect(Collectors.toList());
        final int size = propNames.stream().max(Comparator.comparing(String::length)).orElse("").length();
        final String format = "%" + size + "." + size + "s [%s]";

        propNames.stream().sorted().forEach(propName -> {
            String propValue = getProperty(propName);
            // I want to display the actual content of the line separator...
            if (propName.equals("line.separator")) {
                StringBuilder hex = new StringBuilder(3);
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
        return getSystemProperties().stream().collect(Collectors.joining(System.lineSeparator()));
    }

    public static String getRamInfo() {
        final double freeMG = Runtime.getRuntime().freeMemory();
        final double totalMG = Runtime.getRuntime().totalMemory();
        return format("%s of %s", toByteSize(totalMG - freeMG), toByteSize(totalMG));
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
                FQ_HOSTNAME_REGEX.matcher(serverName).matches();
    }

    public static boolean isValidUrl(String url) {
        try {
            new URI(url);
            if (url.startsWith("http://") || url.startsWith("https://")) {
                if (url.lastIndexOf('/') > 7) {
                    return isValidIpAddressOrHostName(url.substring(url.indexOf("://") + 3, url.indexOf('/', url.indexOf("://") + 3)));
                } else {
                    return isValidIpAddressOrHostName(url.substring(url.indexOf("://") + 3));
                }
            }
            return false;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private static boolean isLookingLikeAnIpV4(String serverName) {
        return Arrays.stream(serverName.split("\\.")).allMatch(e -> IPV4_REGEX.matcher(e).matches());
    }

    public static boolean isValidToken(String token) throws NoSuchAlgorithmException {
        return (!token.isEmpty() && token.substring(token.length()-1).equals(checksum(token.substring(0, token.length()-1))));
    }

    static String checksum(String input) throws NoSuchAlgorithmException {
        MessageDigest objSHA = MessageDigest.getInstance("SHA-1");
        String hash = new BigInteger(1, objSHA.digest(input.getBytes())).toString(16);
        return hash.substring(hash.length()-1).toUpperCase();
    }

    public static String resolveToken(String tokenServerUrl, String token) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder().build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(format(tokenServerUrl, token)))
                .timeout(Duration.ofSeconds(5))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body().trim();
    }
}
