package mpo.dayon.common.network.message;

import mpo.dayon.assisted.network.NetworkAssistedEngine;

public interface NetworkClipboardRequestMessageHandler extends NetworkMessageHandler {
    /**
     * Should not block as called from the network incoming message thread (!)
     */
    void handleClipboardRequest(NetworkAssistedEngine networkAssistedEngine);

}
