package mpo.dayon.assistant.control;

import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;

import mpo.dayon.assistant.gui.AssistantFrameListener;
import mpo.dayon.assistant.network.NetworkAssistantEngine;
import mpo.dayon.common.concurrent.DefaultThreadFactoryEx;
import mpo.dayon.common.concurrent.Executable;
import mpo.dayon.common.log.Log;
import mpo.dayon.common.network.message.NetworkKeyControlMessage;
import mpo.dayon.common.network.message.NetworkMouseControlMessage;

import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static mpo.dayon.common.network.message.NetworkKeyControlMessage.KeyState.PRESSED;
import static mpo.dayon.common.network.message.NetworkKeyControlMessage.KeyState.RELEASED;

public class ControlEngine implements AssistantFrameListener {
	private final NetworkAssistantEngine network;

	private final ThreadPoolExecutor executor;

	public ControlEngine(NetworkAssistantEngine network) {
		this.network = network;
		executor = new ThreadPoolExecutor(1, 1, 0L, MILLISECONDS, new LinkedBlockingQueue<>());
		executor.setThreadFactory(new DefaultThreadFactoryEx("ControlEngine"));
	}

	@Override
    public void onMouseMove(final int xs, final int ys) {
		executor.execute(new Executable(executor) {
			@Override
            protected void execute() {
				network.sendMouseControl(new NetworkMouseControlMessage(xs, ys));
			}
		});
	}

	@Override
    public void onMousePressed(final int xs, final int ys, final int button) {
		executor.execute(new Executable(executor) {
			@Override
            protected void execute() {
				int xbutton = getActingMouseButton(button);
				if (xbutton != NetworkMouseControlMessage.UNDEFINED) {
					network.sendMouseControl(new NetworkMouseControlMessage(xs, ys, NetworkMouseControlMessage.ButtonState.PRESSED, xbutton));
				}
			}
		});
	}
	
	@Override
    public void onMouseReleased(final int x, final int y, final int button) {
		executor.execute(new Executable(executor) {
			@Override
            protected void execute() {
				int xbutton = getActingMouseButton(button);
				if (xbutton != NetworkMouseControlMessage.UNDEFINED) {
					network.sendMouseControl(new NetworkMouseControlMessage(x, y, NetworkMouseControlMessage.ButtonState.RELEASED, xbutton));
				}
			}
		});
	}
	
	private static int getActingMouseButton(final int button) {
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

	@Override
    public void onMouseWheeled(final int x, final int y, final int rotations) {
		executor.execute(new Executable(executor) {
			@Override
            protected void execute() {
				network.sendMouseControl(new NetworkMouseControlMessage(x, y, rotations));
			}
		});
	}

	/**
	 * Fix missing pair'd PRESSED event from RELEASED
	 */
	private final HashMap<Integer, Character> pressedKeys = new HashMap<>(4);

	/**
	 * From AWT thread (!)
	 */
	@Override
    public void onKeyPressed(final int keyCode, final char keyChar) {
		executor.execute(new Executable(executor) {
			@Override
            protected void execute() {
				pressedKeys.put(keyCode, keyChar);
				network.sendKeyControl(new NetworkKeyControlMessage(PRESSED, keyCode, keyChar));
			}
		});
	}

	/**
	 * From AWT thread (!)
	 */
	@Override
    public void onKeyReleased(final int keyCode, final char keyChar) {
		// -------------------------------------------------------------------------------------------------------------
		// E.g., Windows + R : [Windows.PRESSED] and then the focus is LOST =>
		// missing RELEASED events
		//
		// Currently trying to lease the 'assisted' in a consistent state - not
		// sure I should send the
		// [Windows] key and the like (e.g.,CTRL-ALT-DEL, etc...) at all ...
		// -------------------------------------------------------------------------------------------------------------
		if (keyCode == -1) {
			Log.warn(format("Got keyCode %s keyChar '%s' - releasing all keys", keyCode, keyChar));
			pressedKeys.forEach(this::onKeyReleased);
			return;
		}

		if (!pressedKeys.containsKey(keyCode)) {
			Log.warn(format("Not releasing unpressed keyCode %s keyChar '%s'", keyCode, keyChar));
			return;
		}

		executor.execute(new Executable(executor) {
			@Override
            protected void execute() {
				pressedKeys.remove(keyCode);
				network.sendKeyControl(new NetworkKeyControlMessage(RELEASED, keyCode, keyChar));
			}
		});
	}
}
