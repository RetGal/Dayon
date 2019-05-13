package mpo.dayon.common.concurrent;

import java.util.Random;
import java.util.concurrent.ThreadFactory;

import org.jetbrains.annotations.NotNull;

public class DefaultThreadFactoryEx implements ThreadFactory {
	private final int threadNumber = new Random().nextInt(99);

	private final String namePrefix;

	public DefaultThreadFactoryEx(String name) {
		namePrefix = name + "-";
	}

	@Override
    public Thread newThread(@NotNull Runnable runnable) {
		final Thread thread = new Thread(runnable, namePrefix + threadNumber);

		if (thread.isDaemon()) {
			thread.setDaemon(false);
		}

		if (thread.getPriority() != Thread.NORM_PRIORITY) {
			thread.setPriority(Thread.NORM_PRIORITY);
		}

		return thread;
	}

}
