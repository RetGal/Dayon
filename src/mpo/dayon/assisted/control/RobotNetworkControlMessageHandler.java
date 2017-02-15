package mpo.dayon.assisted.control;

import java.awt.AWTException;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
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
	
	private List<Subscriber> subscribers = new ArrayList<Subscriber>();
	
	public void subscribe(Subscriber subscriber) {
		subscribers.add(subscriber);
	}
	
	public void shout(char bogusChar) {
		for (Subscriber subscriber : subscribers) {
			subscriber.digest(String.valueOf(bogusChar));
		}
	}

	public RobotNetworkControlMessageHandler() {
		try {
			robot = new Robot();
		} catch (AWTException ex) {
			throw new RuntimeException(ex);
		}
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
				Log.warn(message.toString() +" contained an invalid keyCode for "+message.getKeyChar());
				shout(message.getKeyChar());
				if (message.getKeyChar() != KeyEvent.CHAR_UNDEFINED) {
					KeyStroke key = KeyStroke.getKeyStroke(message.getKeyChar());
					if (key.getKeyCode() != Character.MIN_VALUE) {
						Log.warn("retrying with keyCode "+key.getKeyCode());
						robot.keyPress(key.getKeyCode());
					}
				}
			}
		} else if (message.isReleased()) {
			try {
				robot.keyRelease(message.getKeyCode());
			} catch (IllegalArgumentException ex) {
				Log.warn(message.toString() +" contained an invalid keyCode for "+message.getKeyChar());
				shout(message.getKeyChar());
				if (message.getKeyChar() != KeyEvent.CHAR_UNDEFINED) {
					KeyStroke key = KeyStroke.getKeyStroke(message.getKeyChar());
					if (key.getKeyCode() != Character.MIN_VALUE) {
						Log.warn("retrying with keyCode "+key.getKeyCode());
						robot.keyRelease(key.getKeyCode());
					}
				}
			}
			
		}
	}
}
