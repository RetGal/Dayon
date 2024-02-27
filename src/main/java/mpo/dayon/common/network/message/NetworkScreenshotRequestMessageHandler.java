package mpo.dayon.common.network.message;

public interface NetworkScreenshotRequestMessageHandler extends NetworkMessageHandler {
    /**
     * Should not block as called from the network incoming message thread (!)
     */
    void handleScreenshotRequest();
}
