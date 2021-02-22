package mpo.dayon.common.network.message;

public interface NetworkClipboardRequestMessageHandler extends NetworkMessageHandler {
    /**
     * Should not block as called from the network incoming message thread (!)
     */
    void handleClipboardRequest();

}
