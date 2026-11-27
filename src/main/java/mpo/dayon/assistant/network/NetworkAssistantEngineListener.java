package mpo.dayon.assistant.network;

import java.io.IOException;

import mpo.dayon.common.event.Listener;
import mpo.dayon.common.network.transport.SessionTransport;

public interface NetworkAssistantEngineListener extends Listener {
	void onReady();

	/**
	 * Should not block as called from the network receiving thread (!)
	 */
	void onStarting(int port, boolean isPortAccessible);

	/**
	 * Should not block as called from the network receiving thread (!)
	 */
	void onConnecting();

	/**
	 * Should not block as called from the network receiving thread (!)
	 */
	boolean onAccepted(SessionTransport connection, boolean autoAccept);

	/**
	 * Should not block as called from the network receiving thread (!)
	 */
	void onConnected(SessionTransport connection, char osId, String inputLocale, int peerMajorVersion);

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

	void onCheckingPeerStatus(boolean blink);

	void onPeerIsAccessible(String address, int port, boolean isPeerAccessible);

	void onSessionInterrupted();
}
