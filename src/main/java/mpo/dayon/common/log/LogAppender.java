package mpo.dayon.common.log;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;

public abstract class LogAppender {
	private final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss.SSS");

	protected String format(LogLevel level, String message) {
		message = (message == null) ? "" : message;
		return String.format("[%20.20s] [%5.5s] (%s) %s", Thread.currentThread().getName(), level,
				dateFormat.format(Date.from(Instant.now())), message);
	}

	public void append(LogLevel level, String message) {
		this.append(level, message, null);
	}

	public abstract void append(LogLevel level, String message, Throwable error);

}
