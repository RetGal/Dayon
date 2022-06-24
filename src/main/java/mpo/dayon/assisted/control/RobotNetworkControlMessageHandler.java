package mpo.dayon.assisted.control;

import java.awt.AWTException;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import mpo.dayon.common.event.Subscriber;
import mpo.dayon.common.log.Log;
import mpo.dayon.common.network.message.NetworkKeyControlMessage;
import mpo.dayon.common.network.message.NetworkMouseControlMessage;

import static java.awt.event.KeyEvent.*;

public class RobotNetworkControlMessageHandler implements NetworkControlMessageHandler {
	private final Robot robot;

	private final List<Subscriber> subscribers = new ArrayList<>();

	private static final char UNIX_SEPARATOR_CHAR = '/';

	private final Set<Integer> pressedKeys = new HashSet<>();

	public RobotNetworkControlMessageHandler() {
		try {
			robot = new Robot();
		} catch (AWTException ex) {
			throw new IllegalStateException(ex);
		}
	}

	public RobotNetworkControlMessageHandler(Robot robot) {
		this.robot = robot;
	}

	@Override
	public void subscribe(Subscriber subscriber) {
		subscribers.add(subscriber);
	}

	private void shout(char bogusChar) {
		subscribers.forEach(subscriber -> subscriber.digest(String.valueOf(bogusChar)));
	}

	/**
	 * Should not block as called from the network incoming message thread (!)
	 */
	@Override
	public void handleMessage(NetworkMouseControlMessage message) {
		if (message.isPressed()) {
			if (message.isButton1()) {
				robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
			} else if (message.isButton2()) {
				robot.mousePress(InputEvent.BUTTON2_DOWN_MASK);
			} else if (message.isButton3()) {
				robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
			}
		} else if (message.isReleased()) {
			if (message.isButton1()) {
				robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
			} else if (message.isButton2()) {
				robot.mouseRelease(InputEvent.BUTTON2_DOWN_MASK);
			} else if (message.isButton3()) {
				robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
			}
		} else if (message.isWheel()) {
			robot.mouseWheel(message.getRotations());
		}
		robot.mouseMove(message.getX(), message.getY());
	}

	/**
	 * Should not block as called from the network incoming message thread (!)
	 */
	@Override
	public void handleMessage(NetworkKeyControlMessage message) {
		if (message.isPressed()) {
			try {
				pressKey(message);
			} catch (IllegalArgumentException ex) {
				Log.error("Error while handling " + message);
				shout(message.getKeyChar());
			}
		} else if (message.isReleased()) {
			try {
				releaseKey(message);
			} catch (IllegalArgumentException ex) {
				Log.error("Error while handling " + message);
			}
		}
	}

	private void pressKey(NetworkKeyControlMessage message) {
		int keyCode = message.getKeyCode();
		if (keyCode != VK_UNDEFINED) {
			if (keyCode == VK_ALT_GRAPH && File.separatorChar != UNIX_SEPARATOR_CHAR) {
				robot.keyPress(VK_CONTROL);
				pressedKeys.add(VK_CONTROL);
				robot.keyPress(VK_ALT);
				pressedKeys.add(VK_ALT);
				Log.debug("KeyCode ALT_GRAPH " + message);
				return;
			}
			Log.debug("KeyCode " + message);
			try {
				robot.keyPress(keyCode);
				pressedKeys.add(keyCode);
				return;
			} catch (IllegalArgumentException ie) {
				Log.debug("Proceeding with plan B");
			}
		}
		Log.debug("Undefined KeyCode " + message);
		if (message.getKeyChar() != CHAR_UNDEFINED) {
			int dec = message.getKeyChar();
			Log.debug("KeyChar as unicode " + dec + " " + message);
			pressedKeys.forEach(robot::keyRelease);
			typeUnicode(dec);
			pressedKeys.forEach(robot::keyPress);
			return;
		}
		Log.warn("Undefined KeyChar " + message);
	}

	private void typeUnicode(int keyCode) {
		if (File.separatorChar == UNIX_SEPARATOR_CHAR) {
			typeLinuxUnicode(keyCode);
			return;
		}
		typeWindowsUnicode(keyCode);
	}

	private void releaseKey(NetworkKeyControlMessage message) {
		int keyCode = message.getKeyCode();
		if (keyCode != VK_UNDEFINED) {
			if (keyCode == VK_ALT_GRAPH && File.separatorChar != UNIX_SEPARATOR_CHAR) {
				robot.keyRelease(VK_ALT);
				pressedKeys.remove(VK_ALT);
				robot.keyRelease(VK_CONTROL);
				pressedKeys.remove(VK_CONTROL);
				Log.debug("KeyCode ALT_GRAPH " + message);
				return;
			}
			Log.debug("KeyCode " + message);
			try {
				robot.keyRelease(keyCode);
				pressedKeys.remove(keyCode);
			} catch (IllegalArgumentException ie) {
				Log.warn("Error releasing KeyCode " + message);
			}
		}
	}

	/**
	 * Unicode characters are typed in decimal on Windows ä => 228
	 */
	private void typeWindowsUnicode(int keyCode) {
		robot.setAutoDelay(1);
		robot.keyPress(VK_ALT);
		// simulate a numpad key press for each digit
		for (int i = 3; i >= 0; --i) {
			int code = keyCode / (int) (Math.pow(10, i)) % 10 + VK_NUMPAD0;
			robot.keyPress(code);
			robot.keyRelease(code);
		}
		robot.keyRelease(VK_ALT);
	}

	/**
	 * Unicode characters are typed in hex on Linux ä => e4
	 */
	private void typeLinuxUnicode(int keyCode) {
		robot.setAutoDelay(1);
		robot.keyPress(VK_CONTROL);
		robot.keyPress(VK_SHIFT);
		robot.keyPress(VK_U);
		robot.keyRelease(VK_U);
		char[] charArray = Integer.toHexString(keyCode).toCharArray();
		// simulate a key press/release for each char
		// char[] { 'e', '4' }  => keyPress(69), keyRelease(69), keyPress(52), KeRelease(52)
		for (char c : charArray) {
			int code = Character.toUpperCase(c);
			robot.keyPress(code);
			robot.keyRelease(code);
		}
		robot.keyRelease(VK_SHIFT);
		robot.keyRelease(VK_CONTROL);
	}
}
