package mpo.dayon.assistant.network;

import java.io.IOException;
import java.net.Socket;

public class NetworkAssistantEngineAdapter implements NetworkAssistantEngineListener
{
    public void onReady()
    {
    }

    /**
     * Should not block as called from the network receiving thread (!)
     */
    public void onHttpStarting(int port)
    {
    }

    /**
     * Should not block as called from the network receiving thread (!)
     */
    public void onStarting(int port)
    {
    }

    /**
     * Should not block as called from the network receiving thread (!)
     */
    public void onAccepting(int port)
    {
    }

    /**
     * Should not block as called from the network receiving thread (!)
     */
    public boolean onAccepted(Socket connection)
    {
        return true;
    }

    /**
     * Should not block as called from the network receiving thread (!)
     */
    public void onConnected(Socket connection)
    {
    }

    /**
     * Should not block as called from the network receiving thread (!)
     */
    public void onByteReceived(int count)
    {
    }

    public void onIOError(IOException error)
    {
    }
}
