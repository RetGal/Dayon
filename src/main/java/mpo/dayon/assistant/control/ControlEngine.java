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
import mpo.dayon.common.network.message.NetworkKeyControlMessage;
import mpo.dayon.common.network.message.NetworkMouseControlMessage;

public class ControlEngine implements AssistantFrameListener {
	private final NetworkAssistantEngine network;

	private ThreadPoolExecutor executor;

	public ControlEngine(NetworkAssistantEngine network) {
		this.network = network;
	}

	public void start() {
		executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
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
	private final Set<Integer> pressedKeys = new HashSet<>();

	/**
	 * From AWT thread (!)
	 */
	@Override
    public void onKeyPressed(final int keyCode, final char keyChar) {
		executor.execute(new Executable(executor) {
			@Override
            protected void execute() {

				pressedKeys.add(keyCode);
				network.sendKeyControl(new NetworkKeyControlMessage(NetworkKeyControlMessage.KeyState.PRESSED, keyCode, keyChar));
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
			pressedKeys.forEach(pressedKey -> onKeyReleased(pressedKey, keyChar));
			return;
		}

		if (!pressedKeys.contains(keyCode)) {
			onKeyPressed(keyCode, keyChar);
		}

		executor.execute(new Executable(executor) {
			@Override
            protected void execute() {

				pressedKeys.remove(keyCode);
				network.sendKeyControl(new NetworkKeyControlMessage(NetworkKeyControlMessage.KeyState.RELEASED, keyCode, keyChar));
			}
		});
	}
}
