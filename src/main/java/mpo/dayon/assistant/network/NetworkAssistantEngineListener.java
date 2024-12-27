package mpo.dayon.assistant.network;

import java.io.IOException;
import java.net.Socket;

import mpo.dayon.common.event.Listener;

public interface NetworkAssistantEngineListener extends Listener {
	void onReady();

	/**
	 * Should not block as called from the network receiving thread (!)
	 */
	void onStarting(int port);

	/**
	 * Should not block as called from the network receiving thread (!)
	 */
	boolean onAccepted(Socket connection);

	/**
	 * Should not block as called from the network receiving thread (!)
	 */
	void onConnected(Socket connection, char osId, String inputLocale, int peerMajorVersion);

	/**
	 * Should not block as called from the network receiving thread (!)
	 */
	void onByteReceived(int count);

	/**
	 * Should not block as called from the network receiving thread (!)
	 */
	void onClipboardReceived();

	/**
	 * Should not block as called from the network receiving thread (!)
	 */
	void onClipboardSent();

	/**
	 * Should not block as called from the network receiving thread (!)
	 */
	void onResizeScreen(int width, int height);

	/**
	 * Should not block as called from the network receiving thread (!)
	 */
	void onDisconnecting();

	void onTerminating();

	void onIOError(IOException error);

	void onFingerprinted(String fingerprints);

	void onReconfigured(NetworkAssistantEngineConfiguration configuration);
}
