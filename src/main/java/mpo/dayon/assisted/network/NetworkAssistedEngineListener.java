package mpo.dayon.assisted.network;

import mpo.dayon.common.event.Listener;

import java.io.IOException;

public interface NetworkAssistedEngineListener extends Listener {

	/**
	 * Should not block as called from the network receiving thread (!)
	 */
	void onConnecting(String serverName, int serverPort);

	/**
	 * Should not block as called from the network receiving thread (!)
	 */
	void onHostNotFound(String serverName);

	/**
	 * Should not block as called from the network receiving thread (!)
	 */
	void onConnectionTimeout(String serverName, int serverPort);

	/**
	 * Should not block as called from the network receiving thread (!)
	 */
	void onRefused(String serverName, int serverPort);

	/**
	 * Should not block as called from the network receiving thread (!)
	 */
	void onConnected(String fingerprints);

	/**
	 * Should not block as called from the network receiving thread (!)
	 */
	void onDisconnecting();

	/**
	 * Should not block as called from the network receiving thread (!)
	 */
	void onIOError(IOException error);

	void onReconfigured(NetworkAssistedEngineConfiguration configuration);

}
