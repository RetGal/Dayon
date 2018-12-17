package mpo.dayon.assisted.control;

import java.awt.AWTException;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.KeyStroke;

import mpo.dayon.common.event.Subscriber;
import mpo.dayon.common.log.Log;
import mpo.dayon.common.network.NetworkEngine;
import mpo.dayon.common.network.message.NetworkKeyControlMessage;
import mpo.dayon.common.network.message.NetworkMouseControlMessage;

public class RobotNetworkControlMessageHandler implements NetworkControlMessageHandler {
	private final Robot robot;

	private final List<Subscriber> subscribers = new ArrayList<>();
	
	public RobotNetworkControlMessageHandler() {
		try {
			robot = new Robot();
		} catch (AWTException ex) {
			throw new RuntimeException(ex);
		}
	}

	public void subscribe(Subscriber subscriber) {
		subscribers.add(subscriber);
	}

	private void shout(char bogusChar) {
		subscribers.forEach(subscriber -> subscriber.digest(String.valueOf(bogusChar)));
	}

	/**
	 * Should not block as called from the network incoming message thread (!)
	 */
	public void handleMessage(NetworkEngine engine, NetworkMouseControlMessage message) {
		if (message.isPressed()) {
			if (message.isButton1()) {
				robot.mousePress(InputEvent.BUTTON1_MASK);
			} else if (message.isButton2()) {
				robot.mousePress(InputEvent.BUTTON2_MASK);
			} else if (message.isButton3()) {
				robot.mousePress(InputEvent.BUTTON3_MASK);
			}
		} else if (message.isReleased()) {
			if (message.isButton1()) {
				robot.mouseRelease(InputEvent.BUTTON1_MASK);
			} else if (message.isButton2()) {
				robot.mouseRelease(InputEvent.BUTTON2_MASK);
			} else if (message.isButton3()) {
				robot.mouseRelease(InputEvent.BUTTON3_MASK);
			}
		} else if (message.isWheel()) {
			robot.mouseWheel(message.getRotations());
		}
		robot.mouseMove(message.getX(), message.getY());
	}

	/**
	 * Should not block as called from the network incoming message thread (!)
	 */
	public void handleMessage(NetworkEngine engine, NetworkKeyControlMessage message) {
		if (message.isPressed()) {
			try {
				robot.keyPress(message.getKeyCode());
			} catch (IllegalArgumentException ex) {
				Log.warn(message.toString() + " contained an invalid keyCode for " + message.getKeyChar());
				if (message.getKeyChar() != KeyEvent.CHAR_UNDEFINED) {
					KeyStroke key = KeyStroke.getKeyStroke(message.getKeyChar(), 0);
					// plan b
					if (key.getKeyCode() != Character.MIN_VALUE) {
						Log.warn("retrying with keyCode " + key.getKeyCode());
						typeUnicode(key.getKeyCode());
						return;
					}
					shout(message.getKeyChar());
				}
			}
		} else if (message.isReleased()) {
			try {
				robot.keyRelease(message.getKeyCode());
			} catch (IllegalArgumentException ex) {
				Log.warn(message.toString() + " contained an invalid keyCode for " + message.getKeyChar());
			}
		}
	}

	/**
	 * Q&D OS detection
	 */
	private void typeUnicode(int keyCode)
	{
		if (File.separatorChar == '/') {
			typeLinuxUnicode(keyCode);
			return;
		}
		typeWindowsUnicode(keyCode);
	}

	/**
	 * Unicode characters are typed in decimal on Windows ä => 228
	 */
	private void typeWindowsUnicode(int keyCode) {
	    robot.keyPress(KeyEvent.VK_ALT);
	    // simulate a numpad key press for each digit
	    for (int i = 3; i >= 0; --i) {
	        int code = keyCode / (int) (Math.pow(10, i)) % 10 + KeyEvent.VK_NUMPAD0;
	        robot.keyPress(code);
	        robot.keyRelease(code);
	    }
	    robot.keyRelease(KeyEvent.VK_ALT);
	}

	/**
	 * Unicode characters are typed in hex on Linux ä => e4
	 */
	private void typeLinuxUnicode(int keyCode) {
	    robot.keyPress(KeyEvent.VK_CONTROL);
	    robot.keyPress(KeyEvent.VK_SHIFT);
	    robot.keyPress(KeyEvent.VK_U);
	    robot.keyRelease(KeyEvent.VK_U);
	    char[] charArray = Integer.toHexString(keyCode).toCharArray();
	    // simulate a key press/release for each char
    	// char[] { 'e', '4' }  => keyPress(69), keyRelease(69), keyPress(52), KeRelease(52)
	    for (char c : charArray) {
	        int code = (int) Character.toUpperCase(c);
	        robot.keyPress(code);
	        robot.keyRelease(code);
		}
	    robot.keyRelease(KeyEvent.VK_SHIFT);
	    robot.keyRelease(KeyEvent.VK_CONTROL);
	}

}
