package mpo.dayon.common.concurrent;

import java.security.SecureRandom;
import java.util.concurrent.ThreadFactory;

public class DefaultThreadFactoryEx implements ThreadFactory {
	private static final SecureRandom RANDOM = new SecureRandom();
	private final int threadNumber = RANDOM.nextInt(99);
	private final String namePrefix;

	public DefaultThreadFactoryEx(String name) {
		namePrefix = name + "-";
	}

	@Override
    public Thread newThread(Runnable runnable) {
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
