package mpo.dayon.common.concurrent;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import org.jetbrains.annotations.NotNull;

public class DefaultThreadFactoryEx implements ThreadFactory {
	private final AtomicInteger threadNumber = new AtomicInteger(1);

	private final String namePrefix;

	public DefaultThreadFactoryEx(String name) {
		namePrefix = name + "-";
	}

	@Override
    public Thread newThread(@NotNull Runnable runnable) {
		final Thread thread = new Thread(runnable, namePrefix + threadNumber.getAndIncrement());

		if (thread.isDaemon()) {
			thread.setDaemon(false);
		}

		if (thread.getPriority() != Thread.NORM_PRIORITY) {
			thread.setPriority(Thread.NORM_PRIORITY);
		}

		return thread;
	}

}
