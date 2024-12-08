package mpo.dayon.common.log;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public abstract class LogAppender {
	private static final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

	protected String format(LogLevel level, String message) {
		return String.format("[%20.20s] [%5.5s] (%s) %s", Thread.currentThread().getName(), level,
				dateFormat.format(Instant.now()), (message == null) ? "" : message);
	}

	public void append(LogLevel level, String message) {
		this.append(level, message, null);
	}

	public abstract void append(LogLevel level, String message, Throwable error);

}
