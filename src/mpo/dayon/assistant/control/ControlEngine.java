package mpo.dayon.assistant.control;

import java.awt.event.MouseEvent;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import mpo.dayon.assistant.gui.AssistantFrameListener;
import mpo.dayon.assistant.network.NetworkAssistantEngine;
import mpo.dayon.common.concurrent.DefaultThreadFactoryEx;
import mpo.dayon.common.concurrent.Executable;
import mpo.dayon.common.configuration.Configurable;
import mpo.dayon.common.network.message.NetworkKeyControlMessage;
import mpo.dayon.common.network.message.NetworkMouseControlMessage;

public class ControlEngine implements Configurable<ControlEngineConfiguration>, AssistantFrameListener {
	private final NetworkAssistantEngine network;

	private ThreadPoolExecutor executor;

	public ControlEngine(NetworkAssistantEngine network) {
		this.network = network;
	}

	public void configure(ControlEngineConfiguration configuration) {
	}

	public void start() {
		executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());
		executor.setThreadFactory(new DefaultThreadFactoryEx("ControlEngine"));
	}

	public void onMouseMove(final int x, final int y) {
		executor.execute(new Executable(executor, null) {
			protected void execute() throws Exception {
				network.sendMouseControl(new NetworkMouseControlMessage(x, y));
			}
		});
	}

	public void onMousePressed(final int x, final int y, final int button) {
		executor.execute(new Executable(executor, null) {
			protected void execute() throws Exception {
				int xbutton = getActingMouseButton(button);
				if (xbutton != NetworkMouseControlMessage.UNDEFINED) {
					network.sendMouseControl(new NetworkMouseControlMessage(x, y, NetworkMouseControlMessage.ButtonState.PRESSED, xbutton));
				}
			}
		});
	}
	
	public void onMouseReleased(final int x, final int y, final int button) {
		executor.execute(new Executable(executor, null) {
			protected void execute() throws Exception {
				int xbutton = getActingMouseButton(button);
				if (xbutton != NetworkMouseControlMessage.UNDEFINED) {
					network.sendMouseControl(new NetworkMouseControlMessage(x, y, NetworkMouseControlMessage.ButtonState.RELEASED, xbutton));
				}
			}
		});
	}
	
	private int getActingMouseButton(final int button) {
		if (MouseEvent.BUTTON1 == button) {
			return NetworkMouseControlMessage.BUTTON1;
		}
		if (MouseEvent.BUTTON2 == button) {
			return NetworkMouseControlMessage.BUTTON2;
		}
		if (MouseEvent.BUTTON3 == button) {
			return NetworkMouseControlMessage.BUTTON3;
		}
		return NetworkMouseControlMessage.UNDEFINED;
	}

	public void onMouseWheeled(final int x, final int y, final int rotations) {
		executor.execute(new Executable(executor, null) {
			protected void execute() throws Exception {
				network.sendMouseControl(new NetworkMouseControlMessage(x, y, rotations));
			}
		});
	}

	/**
	 * -- Keyboard : very experimental & incomplete
	 * --------------------------------------------------------------------
	 */

	/**
	 * Fix missing pair'd PRESSED event from RELEASED
	 */
	private final Set<Integer> pressedKeys = new HashSet<>();

	/**
	 * From AWT thread (!)
	 */
	public void onKeyPressed(final int keycode, final char keychar) {
		executor.execute(new Executable(executor, null) {
			protected void execute() throws Exception {

				pressedKeys.add(keycode);
				network.sendKeyControl(new NetworkKeyControlMessage(NetworkKeyControlMessage.KeyState.PRESSED, keycode, keychar));
			}
		});
	}

	/**
	 * From AWT thread (!)
	 */
	public void onKeyReleased(final int keycode, final char keychar) {
		// -------------------------------------------------------------------------------------------------------------
		// E.g., Windows + R : [Windows.PRESSED] and then the focus is LOST =>
		// missing RELEASED events
		//
		// Currently trying to lease the 'assisted' in a consistent state - not
		// sure I should send the
		// [Windows] key and the like (e.g.,CTRL-ALT-DEL, etc...) at all ...
		// -------------------------------------------------------------------------------------------------------------
		if (keycode == -1) {
			if (pressedKeys.size() > 0) {
				final Integer[] pkeys = pressedKeys.toArray(new Integer[pressedKeys.size()]);
				for (Integer pkey : pkeys) {
					onKeyReleased(pkey, keychar);
				}
			}
			return;
		}

		if (!pressedKeys.contains(keycode)) {
			onKeyPressed(keycode, keychar);
		}

		executor.execute(new Executable(executor, null) {
			protected void execute() throws Exception {

				pressedKeys.remove(keycode);
				network.sendKeyControl(new NetworkKeyControlMessage(NetworkKeyControlMessage.KeyState.RELEASED, keycode, keychar));
			}
		});
	}
}
