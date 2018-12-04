package mpo.dayon.common.log.console;

import org.jetbrains.annotations.Nullable;

import mpo.dayon.common.log.LogAppender;
import mpo.dayon.common.log.LogLevel;

public class ConsoleAppender extends LogAppender {

	public synchronized void append(LogLevel level, @Nullable String message, @Nullable Throwable error) {
		System.out.println(format(level, message));

		if (error != null) {
			error.printStackTrace(System.out);
		}
	}
}
