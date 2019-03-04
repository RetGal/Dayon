package mpo.dayon.assistant.network.https;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import org.jetbrains.annotations.Nullable;

import mpo.dayon.common.babylon.Babylon;
import mpo.dayon.common.log.Log;
import mpo.dayon.common.utils.SystemUtilities;
import mpo.dayon.common.version.Version;

public class NetworkAssistantHttpsResources {
    private static String prevIpAddress = null;

    private static int prevPort = -1;

    private NetworkAssistantHttpsResources() {
    }

    public static void setup(String ipAddress, int port) {
        final File jnlp = SystemUtilities.getOrCreateAppDirectory("jnlp");
        if (jnlp == null) {
            throw new RuntimeException("No JNLP directory!");
        }
        Log.info("[HTTPS] JNLP resource : [ip:" + ipAddress + "] [port:" + port + "] [path:" + jnlp.getAbsolutePath() + "]");

        if (ipAddress.equals(prevIpAddress) && port == prevPort) {
            Log.debug("[HTTPS] JNLP resource : unchanged");
            return;
        }

        try {
            createHtml(jnlp);
            createFavicon(jnlp);
            final String jarname = createJarName();
            createJnlp(ipAddress, port, jnlp, jarname);
            final File jarfile = new File(jnlp, jarname);

            if (!jarfile.exists()) {
                copyJar(jarname, jarfile);
            } else {
                Log.debug("[HTTPS] JNLP resource : " + jarname + " [ unchanged ]");
            }

            prevIpAddress = ipAddress;
            prevPort = port;
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static void copyJar(String jarname, File jarfile) throws IOException {
        Log.debug("[HTTPS] JNLP resource : " + jarname);
        final InputStream content = createJarInputStream("dayon.jar");
        try (final OutputStream out = new FileOutputStream(jarfile)) {
            final byte[] buffer = new byte[4096];
            int count;
            while ((count = content.read(buffer)) != -1) {
                out.write(buffer, 0, count);
            }
            out.flush();
        }
    }

    private static void createJnlp(String ipAddress, int port, File jnlp, String jarname) throws IOException {
        Log.debug("[HTTPS] JNLP resource : dayon.jnlp");
        final InputStream content = NetworkAssistantHttpsResources.class.getResourceAsStream("dayon.jnlp");
        final BufferedReader in = new BufferedReader(new InputStreamReader(content, StandardCharsets.UTF_8));

        final String sb;
        sb = in.lines().map(line -> line + "\n").collect(Collectors.joining());
        in.close();

        String html = sb;
        html = html.replace("${jarname}", jarname);
        html = html.replace("${ipAddress}", ipAddress);
        html = html.replace("${port}", String.valueOf(port));

        final PrintWriter printer = new PrintWriter(new File(jnlp, "dayon.jnlp"));
        printer.print(html);
        printer.flush();
        printer.close();
    }

    private static void createFavicon(File jnlp) throws IOException {
        Log.debug("[HTTPS] JNLP resource : favicon.ico");
        final InputStream content = NetworkAssistantHttpsResources.class.getResourceAsStream("favicon.ico");
        try (final OutputStream out = new FileOutputStream(new File(jnlp, "favicon.ico"))) {
            final byte[] buffer = new byte[4096];
            int count;
            while ((count = content.read(buffer)) != -1) {
                out.write(buffer, 0, count);
            }
            out.flush();
        }
    }

    private static void createHtml(File jnlp) throws IOException {
        Log.debug("[HTTPS] JNLP resource : dayon.html");
        final int major = Version.get().getMajor();
        final int minor = Version.get().getMinor();

        final String clickMe = Babylon.translate("clickMe");
        final String clickMeMsg = Babylon.translate("clickMe.msg");

        final InputStream content = NetworkAssistantHttpsResources.class.getResourceAsStream("dayon.html");
        final BufferedReader in = new BufferedReader(new InputStreamReader(content, StandardCharsets.UTF_8));

        final String sb;

        sb = in.lines().map(line -> line + "\n").collect(Collectors.joining());

        in.close();

        String html = sb;

        html = html.replace("${major}", String.valueOf(major));
        html = html.replace("${minor}", String.valueOf(minor));

        html = html.replace("${clickMe}", clickMe);
        html = html.replace("${clickMeMsg}", clickMeMsg);

        final PrintWriter printer = new PrintWriter(new File(jnlp, "dayon.html"));
        printer.print(html);
        printer.flush();
        printer.close();
    }

    private static String createJarName() {
        final int major = Version.get().getMajor();
        final int minor = Version.get().getMinor();
        return "dayon." + major + "." + minor + ".jar";
    }

    /**
     * Handle a run from within IDEA (i.e., classes instead of JARs).
     */
    private static InputStream createJarInputStream(String path) throws FileNotFoundException {
        @Nullable final File root = SystemUtilities.getDayonJarPath();
        if (root == null) {
            throw new IllegalArgumentException("Could not find the path [" + path + "]!");
        }

        final File file = new File(root, path);
        if (!file.exists()) {
            throw new IllegalArgumentException("Path [" + path + "] not found [" + file.getAbsolutePath() + "]!");
        }
        return new FileInputStream(file);
    }

}