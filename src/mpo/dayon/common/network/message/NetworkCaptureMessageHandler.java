package mpo.dayon.common.network.message;

public interface NetworkCaptureMessageHandler extends NetworkMessageHandler
{
    /**
     * Should not block as called from the network incoming message thread (!)
     */
    void handleCapture(NetworkCaptureMessage capture);
}
