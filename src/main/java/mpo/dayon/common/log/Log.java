package mpo.dayon.common.log;

import mpo.dayon.common.log.console.ConsoleAppender;
import mpo.dayon.common.log.file.FileAppender;

import java.io.File;
import java.io.IOException;

import static java.lang.String.format;
import static java.lang.System.getProperty;

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
                final File logFile = getOrCreateLogFile();
                // console ...
                info("Log logFile : " + logFile.getAbsolutePath());
                out = new FileAppender(logFile.getAbsolutePath());
                // logFile ...
                info("Log logFile : " + logFile.getAbsolutePath());
            } catch (IOException ex) {
                // console ...
                warn("Log file setup error (fallback to console)!", ex);
            }
        }
    }

    private static File getOrCreateLogFile() throws IOException {
        final File file = new File(getProperty("dayon.home"), getProperty("dayon.application.name") + ".log");
        if (file.exists() && file.isDirectory()) {
            throw new IOException(format("Error creating %s", file.getName()));
        }
        return file;
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
