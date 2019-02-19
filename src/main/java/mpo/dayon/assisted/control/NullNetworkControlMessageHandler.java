package mpo.dayon.assisted.control;

import mpo.dayon.common.event.Subscriber;
import mpo.dayon.common.network.NetworkEngine;
import mpo.dayon.common.network.message.NetworkKeyControlMessage;
import mpo.dayon.common.network.message.NetworkMouseControlMessage;

public class NullNetworkControlMessageHandler implements NetworkControlMessageHandler {
	public static final NullNetworkControlMessageHandler INSTANCE = new NullNetworkControlMessageHandler();

	private NullNetworkControlMessageHandler() {
	}

	@Override
	public void handleMessage(NetworkEngine engine, NetworkMouseControlMessage message) {
	}

	@Override
	public void handleMessage(NetworkEngine engine, NetworkKeyControlMessage message) {
	}

	@Override
	public void subscribe(Subscriber listener) {
	}
}
