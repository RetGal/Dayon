package mpo.dayon.common.log;

import org.jetbrains.annotations.Nullable;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public abstract class LogAppender
{
    public static final DateFormat DATE_FORMAT = new SimpleDateFormat("HH:mm:ss.SSS");


    public String format(LogLevel level, @Nullable String message)
    {
        message = (message == null) ? "" : message;
        return String.format("[%20.20s] [%5.5s] (%s) %s", Thread.currentThread().getName(), level, DATE_FORMAT.format(new Date()), message);
    }

    public void append(LogLevel level, @Nullable String message)
    {
        this.append(level, message, null);
    }

    public abstract void append(LogLevel level, @Nullable String message, @Nullable Throwable error);


}
