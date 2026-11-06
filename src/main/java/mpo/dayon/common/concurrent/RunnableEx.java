package mpo.dayon.common.concurrent;

import mpo.dayon.common.error.ErrorHandler;
import mpo.dayon.common.error.FatalErrorHandler;
import mpo.dayon.common.log.Log;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.SocketException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

public abstract class RunnableEx implements Runnable {
	protected RunnableEx() {
	}

	@Override
    public final void run() {
		try {
			doRun();
		} catch (SocketException | SSLException ex) {
			Log.error(ex.getMessage());
		} catch (InterruptedException ex) {
			Log.error("Interrupting [" + Thread.currentThread().getName() + "]");
			Thread.currentThread().interrupt();
		} catch (IOException ex) {
			Log.error(ex.getMessage());
            ErrorHandler.error("An I/O error occurred", ex);
		} catch (Exception ex) {
			FatalErrorHandler.bye("The [" + Thread.currentThread().getName() + "] thread is dead!", ex);
		}
	}

	protected abstract void doRun() throws IOException, InterruptedException, NoSuchAlgorithmException, KeyManagementException;
}
