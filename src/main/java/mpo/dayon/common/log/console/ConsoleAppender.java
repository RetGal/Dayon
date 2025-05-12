package mpo.dayon.common.log.console;

import mpo.dayon.common.log.LogAppender;
import mpo.dayon.common.log.LogLevel;

public class ConsoleAppender extends LogAppender {

	@Override
	@SuppressWarnings("squid:S106")
    public synchronized void append(LogLevel level, String message, Throwable error) {
		System.out.println(format(level, message));

		if (error != null) {
			error.printStackTrace(System.out);
		}
	}
}
