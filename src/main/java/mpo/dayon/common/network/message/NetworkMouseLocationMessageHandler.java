package mpo.dayon.common.network.message;

public interface NetworkMouseLocationMessageHandler extends NetworkMessageHandler {
	/**
	 * Should not block as called from the network incoming message thread (!)
	 */
	void handleLocation(NetworkMouseLocationMessage mouse);
}
