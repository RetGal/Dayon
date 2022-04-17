package mpo.dayon.common.network.message;

public enum NetworkMessageType {
	/**
	 * Introduction message.
	 */
	HELLO,

	/**
	 * An actual capture (made of dirty tiles).
	 */
	CAPTURE,

	/**
	 * The parameters of the captures (e.g., gray levels, ticks, etc...)
	 */
	CAPTURE_CONFIGURATION,

	/**
	 * The parameters of the capture compression (e.g., ZIP vs BZIP2, etc...)
	 */
	COMPRESSOR_CONFIGURATION,

	/**
	 * The mouse X/Y.
	 */
	MOUSE_LOCATION,

	/**
	 * A mouse control event (i.e., assistant to assisted).
	 */
	MOUSE_CONTROL,

	/**
	 * A keyboard control event (i.e., assistant to assisted).
	 */
	KEY_CONTROL,

	/**
	 * A remote clipboard transfer request event.
	 */
	CLIPBOARD_REQUEST,

	/**
	 * A clipboard transfer text event.
	 */
	CLIPBOARD_TEXT,

	/**
	 * A clipboard transfer files event.
	 */
	CLIPBOARD_FILES,

	/**
	 * A ping.
	 */
	PING,

	/**
	 * A screen resize event.
	 */
	RESIZE
}