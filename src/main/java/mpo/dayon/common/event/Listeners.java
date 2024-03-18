package mpo.dayon.common.event;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class Listeners<T extends Listener> {

	private final List<T> bucket = new CopyOnWriteArrayList<>();

	public List<T> getListeners() {
		return Collections.unmodifiableList(bucket);
	}
	
	public void add(T listener) {
		if (listener != null) {
			bucket.add(listener);
		}
	}

	public void remove(T listener) {
		if (listener != null) {
			bucket.remove(listener);
		}
	}

}
