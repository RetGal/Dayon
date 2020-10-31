package mpo.dayon.common.concurrent;

import mpo.dayon.common.error.FatalErrorHandler;

public abstract class RunnableEx implements Runnable {
	protected RunnableEx() {
	}

	@Override
    public final void run() {
		try {
			doRun();
		} catch (Exception ex) {
			FatalErrorHandler.bye("The [" + Thread.currentThread().getName() + "] thread is dead!", ex);
		}
	}

	protected abstract void doRun() throws Exception;
}
