package mpo.dayon.common.log;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;

public abstract class LogAppender {
	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("HH:mm:ss.SSS");

	protected String format(LogLevel level, String message) {
		message = (message == null) ? "" : message;
		return String.format("[%20.20s] [%5.5s] (%s) %s", Thread.currentThread().getName(), level,
				DATE_FORMAT.format(Date.from(Instant.now())), message);
	}

	public void append(LogLevel level, String message) {
		this.append(level, message, null);
	}

	public abstract void append(LogLevel level, String message, Throwable error);

}
