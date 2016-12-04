package mpo.dayon.common.network.message;

import mpo.dayon.common.network.NetworkEngine;

public interface NetworkCaptureConfigurationMessageHandler extends NetworkMessageHandler
{
    /**
     * Should not block as called from the network incoming message thread (!)
     */
    void handleConfiguration(NetworkEngine engine, NetworkCaptureConfigurationMessage configuration);
}