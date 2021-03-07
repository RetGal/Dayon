package mpo.dayon.assisted.control;

import java.awt.AWTException;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import mpo.dayon.common.event.Subscriber;
import mpo.dayon.common.log.Log;
import mpo.dayon.common.network.message.NetworkKeyControlMessage;
import mpo.dayon.common.network.message.NetworkMouseControlMessage;

import static java.awt.event.KeyEvent.*;

public class RobotNetworkControlMessageHandler implements NetworkControlMessageHandler {
	private final Robot robot;

	private final List<Subscriber> subscribers = new ArrayList<>();
	
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
				Log.error("Error while handling " + message.toString());
			}
		} else if (message.isReleased()) {
			try {
				releaseKey(message);
			} catch (IllegalArgumentException ex) {
				Log.error("Error while handling " + message.toString());
			}
		}
	}

	private void pressKey(NetworkKeyControlMessage message) {
		if (message.getKeyChar() != CHAR_UNDEFINED && message.getKeyCode() != VK_WINDOWS) {
			int dec = message.getKeyChar();
			if ((dec <= 48 || dec >= 57) && (dec <= 64 || dec >= 91) && (dec <= 96 || dec >= 123)) {
				typeUnicode(dec);
				return;
			}
		}
		if (message.getKeyCode() != VK_UNDEFINED && message.getKeyCode() != KeyEvent.VK_ALT_GRAPH) {
			robot.keyPress(message.getKeyCode());
		}
	}

	private void typeUnicode(int keyCode) {
		if (File.separatorChar == '/') {
			typeLinuxUnicode(keyCode);
			return;
		}
		typeWindowsUnicode(keyCode);
	}

	private void releaseKey(NetworkKeyControlMessage message) {
		if (message.getKeyChar() != CHAR_UNDEFINED && message.getKeyCode() != VK_WINDOWS) {
			int dec = message.getKeyChar();
			if ((dec <= 48 || dec >= 57) && (dec <= 64 || dec >= 91) && (dec <= 96 || dec >= 123)) {
				releaseUnicode();
				return;
			}
		}
		if (message.getKeyCode() != VK_UNDEFINED && message.getKeyCode() != VK_ALT_GRAPH) {
			robot.keyRelease(message.getKeyCode());
		}
	}

	private void releaseUnicode() {
		if (File.separatorChar == '/') {
			releaseLinuxUnicode();
			return;
		}
		releaseWindowsUnicode();
	}

	/**
	 * Unicode characters are typed in decimal on Windows ä => 228
	 */
	private void typeWindowsUnicode(int keyCode) {
	    robot.keyPress(VK_ALT);
	    // simulate a numpad key press for each digit
	    for (int i = 3; i >= 0; --i) {
	        int code = keyCode / (int) (Math.pow(10, i)) % 10 + VK_NUMPAD0;
	        robot.keyPress(code);
	        robot.keyRelease(code);
	    }
		// will be released when handling the subsequent message
	}

	private void releaseWindowsUnicode() {
		robot.keyRelease(VK_ALT);
	}

	/**
	 * Unicode characters are typed in hex on Linux ä => e4
	 */
	private void typeLinuxUnicode(int keyCode) {
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
		// will be released when handling the subsequent message
	}

	private void releaseLinuxUnicode() {
		robot.keyRelease(VK_CONTROL);
		robot.keyRelease(VK_SHIFT);
	}
}
