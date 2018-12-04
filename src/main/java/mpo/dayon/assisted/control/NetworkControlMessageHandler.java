package mpo.dayon.assisted.control;

import mpo.dayon.common.event.Subscriber;
import mpo.dayon.common.network.NetworkEngine;
import mpo.dayon.common.network.message.NetworkKeyControlMessage;
import mpo.dayon.common.network.message.NetworkMessageHandler;
import mpo.dayon.common.network.message.NetworkMouseControlMessage;

public interface NetworkControlMessageHandler extends NetworkMessageHandler {
	/**
	 * Should not block as called from the network incoming message thread (!)
	 */
	void handleMessage(NetworkEngine engine, NetworkMouseControlMessage message);

	/**
	 * Should not block as called from the network incoming message thread (!)
	 */
	void handleMessage(NetworkEngine engine, NetworkKeyControlMessage message);
	
	/**
	 * Adds an event listener
	 */
	void subscribe(Subscriber listener);

}
