package mpo.dayon.common.concurrent;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;

import org.jetbrains.annotations.Nullable;

import mpo.dayon.common.log.Log;

public abstract class Executable extends RunnableEx {
	private final ExecutorService executor;

	/**
	 * Limiting access to an unbounded queue (!)
	 */
	@Nullable
	private final Semaphore semaphore;

	protected Executable(ExecutorService executor) {
		this.executor = executor;
		this.semaphore = null;
	}

	public Executable(ExecutorService executor, @Nullable Semaphore semaphore) {
		this.executor = executor;
		this.semaphore = semaphore;
	}

	@Override
    public final void doRun() throws IOException, InterruptedException {
		try {
			if (semaphore != null) {
				semaphore.release();
			}

			execute();
		} catch (InterruptedException ex) {
			if (!executor.isShutdown()) // executor.shutdownNow() ...
			{
				throw ex;
			}

			Log.info(Thread.currentThread().getName() + " has cancelled a task (shutdown)!");
		}
	}

	protected abstract void execute() throws IOException, InterruptedException;
}