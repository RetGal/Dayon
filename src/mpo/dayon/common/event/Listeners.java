package mpo.dayon.common.event;

import java.lang.reflect.Array;

import org.jetbrains.annotations.Nullable;

public class Listeners<T extends Listener> {
	private final Class<?> clazz;

	@Nullable
	private T[] listeners;

	public Listeners(Class<?> clazz) {
		this.clazz = clazz;
	}

	@Nullable
	public T[] getListeners() {
		return listeners;
	}

	public synchronized void add(T listener) {
		if (listener == null) {
			return;
		}

		if (listeners == null) {
			final T[] tmp = (T[]) Array.newInstance(clazz, 1);
			tmp[0] = listener;

			listeners = tmp;
			return;
		}

		final T[] xlisteners = listeners;

		final int len = xlisteners.length;
		final T[] tmp = (T[]) Array.newInstance(clazz, len + 1);

		System.arraycopy(xlisteners, 0, tmp, 0, len);
		tmp[len] = listener;

		listeners = tmp;
	}

	public synchronized void remove(T listener) {
		if (listener == null || listeners == null) {
			return;
		}

		final T[] xlisteners = listeners;

		int pos = -1;

		for (int idx = 0; idx < xlisteners.length; idx++) {
			if (listener == xlisteners[idx]) {
				pos = idx;
				break;
			}
		}

		if (pos > -1) {
			final T[] tmp = (T[]) Array.newInstance(clazz, xlisteners.length - 1);
			System.arraycopy(xlisteners, 0, tmp, 0, pos);

			if (pos < tmp.length) {
				System.arraycopy(xlisteners, pos + 1, tmp, pos, tmp.length - pos);
			}

			listeners = (tmp.length == 0) ? null : tmp;
		}
	}
}
