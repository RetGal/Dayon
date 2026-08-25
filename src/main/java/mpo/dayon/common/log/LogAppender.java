package mpo.dayon.common.log;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public abstract class LogAppender {
	private static final ThreadLocal<DateTimeFormatter> dateFormat = ThreadLocal.withInitial(() ->
			DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault()));
	private static final ThreadLocal<StringBuilder> builder = ThreadLocal.withInitial(StringBuilder::new);

	protected String format(LogLevel level, String message) {
		StringBuilder sb = builder.get();
		sb.setLength(0);

		String threadName = Thread.currentThread().getName();
		sb.append(String.format("[%20.20s]", threadName));
		sb.append(String.format(" [%5.5s]", level));
		sb.append(" (");
		sb.append(dateFormat.get().format(Instant.now()));
		sb.append(") ");
		if (message != null) {
			sb.append(message);
		}
		return sb.toString();
	}

	public void append(LogLevel level, String message) {
		this.append(level, message, null);
	}

	public abstract void append(LogLevel level, String message, Throwable error);

	// Cleanup ThreadLocal resources. Should be called on application shutdown.
	public static void cleanup() {
		dateFormat.remove();
		builder.remove();
	}

}
