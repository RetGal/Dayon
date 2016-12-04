package mpo.dayon.common.network.message;

import mpo.dayon.common.network.NetworkEngine;

public interface NetworkCompressorConfigurationMessageHandler extends NetworkMessageHandler
{
    /**
     * Should not block as called from the network incoming message thread (!)
     */
    void handleConfiguration(NetworkEngine engine, NetworkCompressorConfigurationMessage configuration);
}