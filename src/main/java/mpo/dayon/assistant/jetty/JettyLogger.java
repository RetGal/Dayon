package mpo.dayon.assistant.jetty;

import org.eclipse.jetty.util.log.Logger;

import mpo.dayon.common.log.Log;

import java.util.Arrays;
import java.util.stream.Collectors;

public class JettyLogger implements Logger {
    private final static String PREFIX = "[JETTY] ";

    @Override
    public Logger getLogger(String name) {
        return this;
    }

    @Override
    public void ignore(Throwable throwable) {
        //
    }

    @Override
    public boolean isDebugEnabled() {
        return Log.isDebugEnabled();
    }

    @Override
    public void setDebugEnabled(boolean enabled) {
        //
    }

    @Override
    public void debug(String message, Object... objects) {
        if (isDebugEnabled()) {
            Log.debug(PREFIX + format(message, objects));
        }
    }

    @Override
    public void debug(Throwable throwable) {
        if (isDebugEnabled()) {
            Log.debug(PREFIX + throwable);
        }
    }

    @Override
    public void debug(String message, Throwable throwable) {
        if (isDebugEnabled()) {
            Log.debug(PREFIX + message, throwable);
        }
    }

    @Override
    public void debug(String message, long arg) {
        if (isDebugEnabled()) {
            Log.debug(PREFIX + format(message, arg));
        }
    }

    @Override
    public String getName() {
        return "JettyLogger";
    }

    @Override
    public void warn(String message, Object... objects) {
        Log.warn(PREFIX + format(message, objects));
    }

    @Override
    public void warn(Throwable throwable) {
        Log.warn(PREFIX + throwable);
    }

    @Override
    public void warn(String message, Throwable throwable) {
        Log.warn(PREFIX + message, throwable);
    }

    @Override
    public void info(String s, Object... objects) {
        Log.info(PREFIX + format(s, objects));
    }

    @Override
    public void info(Throwable throwable) {
        Log.info(PREFIX + throwable);
    }

    @Override
    public void info(String message, Throwable throwable) {
        Log.info(PREFIX + message + throwable);
    }

    private String format(String message, Object... args) {
        return Arrays.stream(args).map(arg -> ", " + arg).collect(Collectors.joining("", message, ""));
    }

}
