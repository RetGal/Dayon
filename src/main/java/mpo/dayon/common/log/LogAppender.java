package mpo.dayon.common.log;

import java.text.SimpleDateFormat;
import java.util.Date;

public abstract class LogAppender {
	// SimpleDateFormat used instead of java.time for performance in hot logging path
	@SuppressWarnings("java:S2143")
	private static final ThreadLocal<SimpleDateFormat> dateFormat = ThreadLocal.withInitial(() ->
			new SimpleDateFormat("HH:mm:ss.SSS"));
	private static final ThreadLocal<StringBuilder> builder = ThreadLocal.withInitial(StringBuilder::new);

	protected String format(LogLevel level, String message) {
		StringBuilder sb = builder.get();
		sb.setLength(0);

		String threadName = Thread.currentThread().getName();
		sb.append(String.format("[%20.20s]", threadName));
		sb.append(String.format(" [%5.5s]", level));
		sb.append(" (");
		sb.append(dateFormat.get().format(new Date()));
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
