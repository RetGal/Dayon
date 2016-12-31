package mpo.dayon.assistant.jetty;

import org.eclipse.jetty.util.log.Logger;

import mpo.dayon.common.log.Log;

public class JettyLogger implements Logger {
	@Override
	public Logger getLogger(String name) {
		return this;
	}

	@Override
	public void ignore(Throwable throwable) {
		//
	}

	@Override
	public boolean isDebugEnabled() {
		return Log.isDebugEnabled();
	}

	@Override
	public void setDebugEnabled(boolean enabled) {
		//
	}

	@Override
	public void debug(String message, Object... objects) {
		if (isDebugEnabled()) {
			Log.debug("[JETTY] " + format(message, objects));
		}
	}

	@Override
	public void debug(Throwable throwable) {
		if (isDebugEnabled()) {
			Log.debug("[JETTY] " + throwable);
		}
	}

	@Override
	public void debug(String message, Throwable throwable) {
		if (isDebugEnabled()) {
			Log.debug("[JETTY] " + message, throwable);
		}
	}

	@Override
	public String getName() {
		return "JettyLogger";
	}

	@Override
	public void warn(String message, Object... objects) {
		Log.warn("[JETTY] " + format(message, objects));
	}

	@Override
	public void warn(Throwable throwable) {
		Log.warn("[JETTY] " + throwable);
	}

	@Override
	public void warn(String message, Throwable throwable) {
		Log.warn("[JETTY] " + message, throwable);
	}

	@Override
	public void info(String s, Object... objects) {
		Log.info("[JETTY] " + format(s, objects));
	}

	@Override
	public void info(Throwable throwable) {
		Log.info("[JETTY] " + throwable);
	}

	@Override
	public void info(String message, Throwable throwable) {
		Log.info("[JETTY] " + message + throwable);
	}

	private String format(String message, Object... args) {
		StringBuilder mess = new StringBuilder(message);
		for (Object arg : args) {
			mess.append(", ");
			mess.append(arg);
		}
		return mess.toString();
	}

}
