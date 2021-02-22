package mpo.dayon.common.network.message;

public interface NetworkCaptureConfigurationMessageHandler extends NetworkMessageHandler {
	/**
	 * Should not block as called from the network incoming message thread (!)
	 */
	void handleConfiguration(NetworkCaptureConfigurationMessage configuration);
}