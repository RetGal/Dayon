package mpo.dayon.assistant.network;

import mpo.dayon.common.event.Listener;

import java.io.IOException;
import java.net.Socket;

public interface NetworkAssistantEngineListener extends Listener
{
    void onReady();

    /**
     * Should not block as called from the network receiving thread (!)
     */
    void onHttpStarting(int port);

    /**
     * Should not block as called from the network receiving thread (!)
     */
    void onStarting(int port);

    /**
     * Should not block as called from the network receiving thread (!)
     */
    void onAccepting(int port);

    /**
     * Should not block as called from the network receiving thread (!)
     */
    boolean onAccepted(Socket connection);

    /**
     * Should not block as called from the network receiving thread (!)
     */
    void onConnected(Socket connection);

    /**
     * Should not block as called from the network receiving thread (!)
     */
    void onByteReceived(int count);

    void onIOError(IOException error);
}
