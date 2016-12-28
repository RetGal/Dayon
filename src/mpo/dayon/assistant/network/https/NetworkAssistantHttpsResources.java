package mpo.dayon.assistant.network.https;

import mpo.dayon.common.babylon.Babylon;
import mpo.dayon.common.log.Log;
import mpo.dayon.common.utils.SystemUtilities;
import mpo.dayon.common.version.Version;
import org.jetbrains.annotations.Nullable;

import java.io.*;

public class NetworkAssistantHttpsResources
{
    private static String prevIpAddress = null;

    private static int prevPort = -1;

    public static void setup(String ipAddress, int port)
    {
        final File jnlp = SystemUtilities.getOrCreateAppDirectory("jnlp");
        if (jnlp == null)
        {
            throw new RuntimeException("No JNLP directory!");
        }

        Log.warn("[HTTPS] JNLP resource : [ip:" + ipAddress + "] [port:" + port + "] [path:" + jnlp.getAbsolutePath() + "]");

        if (ipAddress.equals(prevIpAddress) && port == prevPort)
        {
            Log.warn("[HTTPS] JNLP resource : unchanged");
            return;
        }

        try
        {
            /**
             * I keep that .html file : can be bookmark'd - the .jnlp alone cannot be.
             */

            Log.warn("[HTTPS] JNLP resource : dayon.html");
            {
                final int major = Version.get().getMajor();
                final int minor = Version.get().getMinor();

                final String clickMe = Babylon.translate("clickMe");
                final String clickMeMsg = Babylon.translate("clickMe.msg");

                final InputStream content = NetworkAssistantHttpsResources.class.getResourceAsStream("dayon.html");
                final BufferedReader in = new BufferedReader(new InputStreamReader(content, "UTF-8"));

                final StringBuilder sb = new StringBuilder();

                String line;
                while ((line = in.readLine()) != null)
                {
                    sb.append(line);
                    sb.append("\n");
                }

                in.close();

                String html = sb.toString();

                html = html.replace("${major}", String.valueOf(major));
                html = html.replace("${minor}", String.valueOf(minor));

                html = html.replace("${clickMe}", clickMe);
                html = html.replace("${clickMeMsg}", clickMeMsg);

                final PrintWriter printer = new PrintWriter(new File(jnlp, "dayon.html"));
                printer.print(html);
                printer.flush();
                printer.close();
            }

            Log.warn("[HTTPS] JNLP resource : favicon.ico");
            {
                final InputStream content = NetworkAssistantHttpsResources.class.getResourceAsStream("favicon.ico");
                final OutputStream out = new FileOutputStream(new File(jnlp, "favicon.ico"));

                final byte[] buffer = new byte[4096];

                int count;
                while ((count = content.read(buffer)) != -1)
                {
                    out.write(buffer, 0, count);
                }

                out.flush();
                out.close();
            }

            final String jarname = createJarName();

            Log.warn("[HTTPS] JNLP resource : dayon.jnlp");
            {
                final InputStream content = NetworkAssistantHttpsResources.class.getResourceAsStream("dayon.jnlp");
                final BufferedReader in = new BufferedReader(new InputStreamReader(content, "UTF-8"));

                final StringBuilder sb = new StringBuilder();

                String line;
                while ((line = in.readLine()) != null)
                {
                    sb.append(line);
                    sb.append("\n");
                }

                in.close();

                String html = sb.toString();

                html = html.replace("${jarname}", jarname);

                html = html.replace("${ipAddress}", ipAddress);
                html = html.replace("${port}", String.valueOf(port));

                final PrintWriter printer = new PrintWriter(new File(jnlp, "dayon.jnlp"));
                printer.print(html);
                printer.flush();
                printer.close();
            }

            final File jarfile = new File(jnlp, jarname);
            if (!jarfile.exists())
            {
                Log.warn("[HTTPS] JNLP resource : " + jarname);
                {
                    final InputStream content = createJarInputStream("dayon.jar");
                    final OutputStream out = new FileOutputStream(jarfile);

                    final byte[] buffer = new byte[4096];

                    int count;
                    while ((count = content.read(buffer)) != -1)
                    {
                        out.write(buffer, 0, count);
                    }

                    out.flush();
                    out.close();
                }
            }
            else
            {
                Log.warn("[HTTPS] JNLP resource : " + jarname + " [ unchanged ]");
            }

            prevIpAddress = ipAddress;
            prevPort = port;
        }
        catch (IOException ex)
        {
            throw new RuntimeException(ex);
        }
    }

    private static String createJarName()
    {
        final int major = Version.get().getMajor();
        final int minor = Version.get().getMinor();

        return "dayon." + major + "." + minor + ".jar";
    }

    /**
     * Handle a run from within IDEA (i.e., classes instead of JARs).
     */
    private static InputStream createJarInputStream(String path) throws FileNotFoundException
    {
        @Nullable
        final File root = SystemUtilities.getDayonJarPath();

        if (root == null)
        {
            throw new RuntimeException("Could not find the path [" + path + "]!");
        }

        final File file = new File(root, path);

        if (!file.exists())
        {
            throw new RuntimeException("Path [" + path + "] not found [" + file.getAbsolutePath() + "]!");
        }

        return new FileInputStream(file);
    }

}