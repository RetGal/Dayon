package mpo.dayon.common.log;

import mpo.dayon.common.log.console.ConsoleAppender;
import mpo.dayon.common.log.file.FileAppender;
import mpo.dayon.common.utils.SystemUtilities;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Minimal logging interface - minimize the JAR size on the assisted side - at
 * the same time I need a very simple stuff on the assistant side - let's see
 * later (!)
 */
public final class Log {
    private static final boolean DEBUG = System.getProperty("dayon.debug") != null;

    private static LogAppender out;

    private Log() {
    }

    static {
        final String mode = System.getProperty("dayon.log", "console");
        out = new ConsoleAppender();

        if ("file".equals(mode)) {
            try {
                final String filename = System.getProperty("dayon.application.name") + ".log";
                final File file = SystemUtilities.getOrCreateAppFile(filename);

                if (file == null) {
                    throw new FileNotFoundException(filename);
                }
                // console ...
                info("Log file : " + file.getAbsolutePath());
                out = new FileAppender(file.getAbsolutePath());
                // file ...
                info("Log file : " + file.getAbsolutePath());
            } catch (FileNotFoundException ex) {
                // console ...
                warn("Log file setup error (fallback to console)!", ex);
            }
        }
    }

    public static boolean isDebugEnabled() {
        return DEBUG;
    }

    public static void debug(String message) {
        if (DEBUG) {
            out.append(LogLevel.DEBUG, message);
        }
    }

    public static void debug(String message, Throwable error) {
        if (DEBUG) {
            out.append(LogLevel.DEBUG, message, error);
        }
    }

    public static void info(String message) {
        out.append(LogLevel.INFO, message);
    }

    public static void warn(String message) {
        out.append(LogLevel.WARN, message);
    }

    public static void warn(Throwable error) {
        out.append(LogLevel.WARN, error.getMessage(), error);
    }

    public static void warn(String message, Throwable error) {
        out.append(LogLevel.WARN, message, error);
    }

    public static void error(String message) {
        out.append(LogLevel.ERROR, message);
    }

    public static void error(String message, Throwable error) {
        out.append(LogLevel.ERROR, message, error);
    }

    public static void fatal(String message) {
        out.append(LogLevel.FATAL, message);
    }

    public static void fatal(String message, Throwable error) {
        out.append(LogLevel.FATAL, message, error);
    }

}
