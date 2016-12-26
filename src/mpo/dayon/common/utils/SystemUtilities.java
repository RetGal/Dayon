package mpo.dayon.common.utils;

import com.sun.jmx.mbeanserver.GetPropertyAction;
import mpo.dayon.common.babylon.Babylon;
import mpo.dayon.common.log.Log;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.metal.MetalLookAndFeel;
import java.io.*;
import java.net.*;
import java.security.AccessController;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

public abstract class SystemUtilities
{
    @Nullable
    private static File getInstallRoot()
    {
        try
        {
            final ClassLoader cl = Thread.currentThread().getContextClassLoader();

            File rootPATH = null;

            if (cl instanceof URLClassLoader)
            {
                final URLClassLoader ucl = (URLClassLoader) cl;
                final URL[] urls = ucl.getURLs();

                for (final URL url : urls) {
                    if ("file".equals(url.getProtocol())) {
                        final String path = url.toExternalForm();

                        if (path.contains("/out/idea/")) {
                            final int pos = path.indexOf("/out");
                            rootPATH = new File(new URI(path.substring(0, pos)));
                            break;
                        } else if (path.contains("/lib/dayon.jar")) {
                            final int pos = path.indexOf("/lib");
                            rootPATH = new File(new URI(path.substring(0, pos)));
                            break;
                        }
                    }
                }
            }

            return rootPATH;
        }
        catch (URISyntaxException ex)
        {
            throw new RuntimeException(ex); // unlikely (!)
        }
    }

    @Nullable
    public static File getDayonJarPath()
    {
        final File root = SystemUtilities.getInstallRoot();
        if (root == null)
        {
            return null;
        }

        File path = new File(root, "out/ant/jars");
        if (path.exists() && path.isDirectory())
        {
            return path;
        }

        path = new File(root, "lib");
        if (path.exists() && path.isDirectory())
        {
            return path;
        }

        return null;
    }

    @Nullable
    public static URI getLocalIndexHtml()
    {
        @Nullable
        final File rootPATH = getInstallRoot();

        if (rootPATH != null)
        {
            // Anchor not supported : #assistant-setup

            return new File(rootPATH, "doc/html/" + Babylon.translate("quickstart.html")).toURI();
        }

        return null;
    }

    @Nullable
    private static synchronized File getOrCreateAppDir()
    {
        final String homeDir = System.getProperty("user.home"); // *.log4j.xml are using that one (!)
        if (homeDir == null)
        {
            Log.warn("Home directory [user.home] is null!");
            return null;
        }

        final File home = new File(homeDir);
        if (!home.isDirectory())
        {
            Log.warn("Home directory [" + homeDir + "] is not a directory!");
            return null;
        }

        final File appDir = new File(home, ".dayon");
        if (!appDir.exists())
        {
            if (!appDir.mkdir())
            {
                Log.warn("Could not create the application directory [" + appDir.getAbsolutePath() + "]!");
                return null;
            }
        }

        return appDir;
    }

    @Nullable
    public static File getOrCreateAppDirectory(String name)
    {
        final File home = getOrCreateAppDir();
        if (home == null)
        {
            Log.warn("Could not create the application directory (1) [" + name + "]!");
            return null;
        }

        final File dir = new File(home, name);

        if (dir.exists() && !dir.isDirectory())
        {
            Log.warn("Could not create the application directory (2) [" + name + "]!");
            return null;
        }

        if (!dir.exists())
        {
            if (!dir.mkdir())
            {
                Log.warn("Could not create the application directory (3) [" + name + "]!");
                return null;
            }
        }

        return dir;
    }

    @Nullable
    public static File getOrCreateAppFile(String name)
    {
        final File home = getOrCreateAppDir();
        if (home == null)
        {
            Log.warn("Could not create the application file (1) [" + name + "]!");
            return null;
        }

        final File file = new File(home, name);

        if (file.exists() && file.isDirectory())
        {
            Log.warn("Could not create the application file (2) [" + name + "]!");
            return null;
        }

        return file;
    }

    public static String getApplicationName()
    {
        final String name = SystemUtilities.getStringProperty(null, "dayon.application.name");
        if (name == null)
        {
            throw new RuntimeException("Missing application name!");
        }
        return name;
    }

    public static void setApplicationName(String name)
    {
        System.setProperty("dayon.application.name", name);
    }

    private static String getStringProperty(@Nullable Properties props, String name)
    {
        final String value = getStringProperty(props, name, null);

        if (value == null)
        {
            throw new RuntimeException("Missing property [" + name + "]!");
        }

        return value;
    }

    public static String getStringProperty(@Nullable Properties props, String name, String defaultValue)
    {
        if (props == null)
        {
            final String prop = System.getProperty(name);

            if (prop == null)
            {
                return System.getProperty("jnlp." + name, defaultValue);
            }

            return prop;
        }

        return props.getProperty(name, defaultValue);
    }

    public static int getIntProperty(@Nullable Properties props, String name, int defaultValue)
    {
        final String prop = getStringProperty(props, name, null);

        if (prop == null)
        {
            return defaultValue;
        }

        return Integer.valueOf(prop);
    }

    public static boolean getBooleanProperty(@Nullable Properties props, String name, boolean defaultValue)
    {
        final String prop = getStringProperty(props, name, null);

        if (prop == null)
        {
            return defaultValue;
        }

        return Boolean.valueOf(prop);
    }

    public static double getDoubleProperty(@Nullable Properties props, String name, double defaultValue)
    {
        final String prop = getStringProperty(props, name, null);

        if (prop == null)
        {
            return defaultValue;
        }

        return Double.valueOf(prop);
    }

    public static <T extends Enum<T>> T getEnumProperty(@Nullable Properties props, String name, T defaultValue, T[] enums)
    {
        final String prop = getStringProperty(props, name, null);

        if (prop == null)
        {
            return defaultValue;
        }

        final int ordinal = Integer.valueOf(prop);

        for (T anEnum : enums) {
            if (ordinal == anEnum.ordinal()) {
                return anEnum;
            }
        }

        return defaultValue;
    }

    public static List<String> getSystemProperties()
    {
        final List<String> props = new ArrayList<>();

        int size = Integer.MIN_VALUE;

        final List<String> propnames = new ArrayList<>();
        for (Object entry : System.getProperties().keySet())
        {
            final String name = entry.toString();
            propnames.add(name);

            if (name.length() > size)
            {
                size = name.length();
            }
        }

        Collections.sort(propnames);

        for (String propname : propnames)
        {
            String propvalue = System.getProperty(propname);

            // I want to display the actual content of the line separator...
            if (propname.equals("line.separator"))
            {
                String hex = "";
                for (int idx = 0; idx < propvalue.length(); idx++)
                {
                    final int cc = propvalue.charAt(idx);
                    hex += "\\" + cc;
                }
                propvalue = hex;
            }

            props.add(String.format("%" + size + "." + size + "s [%s]", propname, propvalue));
        }

        return props;
    }

    public static String getSystemPropertiesEx()
    {
        final StringBuilder sb = new StringBuilder();

        for (String line : getSystemProperties())
        {
            sb.append(line);
            sb.append(getLineSeparator());
        }

        return sb.toString();
    }

    private static String getLineSeparator()
    {
        return AccessController.doPrivileged(new GetPropertyAction("line.separator"));
    }

    public static String getRamInfo()
    {
        final long freeMG = Runtime.getRuntime().freeMemory();
        final long totalMG = Runtime.getRuntime().totalMemory();

        return UnitUtilities.toByteSize(totalMG - freeMG, false) + " of " + UnitUtilities.toByteSize(totalMG, false);
    }

    public static void safeClose(@Nullable Reader in)
    {
        if (in != null)
        {
            try
            {
                in.close();
            }
            catch (IOException ignored)
            {
            }
        }
    }

    public static void safeClose(@Nullable Writer out)
    {
        if (out != null)
        {
            try
            {
                out.close();
            }
            catch (IOException ignored)
            {
            }
        }
    }

    public static void safeClose(@Nullable InputStream in)
    {
        if (in != null)
        {
            try
            {
                in.close();
            }
            catch (IOException ignored)
            {
            }
        }
    }

    public static void safeClose(@Nullable OutputStream out)
    {
        if (out != null)
        {
            try
            {
                out.close();
            }
            catch (IOException ignored)
            {
            }
        }
    }

    public static void safeClose(@Nullable ServerSocket socket)
    {
        if (socket != null)
        {
            try
            {
                socket.close();
            }
            catch (IOException ignored)
            {
            }
        }
    }

    public static void safeClose(@Nullable Socket socket)
    {
        if (socket != null)
        {
            try
            {
                socket.close();
            }
            catch (IOException ignored)
            {
            }
        }
    }

    public static String getDefaultLookAndFeel()
    {
        for (UIManager.LookAndFeelInfo lookAndFeelInfo : UIManager.getInstalledLookAndFeels())
        {
            if (lookAndFeelInfo.getName().equals("Nimbus"))
            {
                return lookAndFeelInfo.getClassName();
            }
        }

        return MetalLookAndFeel.class.getName();
    }

}
