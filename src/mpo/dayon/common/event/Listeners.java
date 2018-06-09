package mpo.dayon.common.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class Listeners<T extends Listener> {
	private final Class<?> clazz;

	private final List<T> listeners = new CopyOnWriteArrayList<T>();

	public Listeners(Class<?> clazz) {
		this.clazz = clazz;
	}

	public List<T> getListeners() {
		return listeners;
	}
	
	public void add(T listener) {
		if (listener != null) {
			listeners.add(listener);
		}
	}

	public void remove(T listener) {
		if (listener != null) {
			listeners.remove(listener);
		}
	}

}
