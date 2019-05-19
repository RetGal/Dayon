package mpo.dayon.common.log;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.jetbrains.annotations.Nullable;

public abstract class LogAppender {
	private static final DateFormat DATE_FORMAT = new SimpleDateFormat("HH:mm:ss.SSS");

	protected String format(LogLevel level, @Nullable String message) {
		message = (message == null) ? "" : message;
		return String.format("[%20.20s] [%5.5s] (%s) %s", Thread.currentThread().getName(), level, DATE_FORMAT.format(new Date()), message);
	}

	public void append(LogLevel level, @Nullable String message) {
		this.append(level, message, null);
	}

	public abstract void append(LogLevel level, @Nullable String message, @Nullable Throwable error);

}
