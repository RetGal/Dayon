package mpo.dayon.common.network.transport.ice;

import java.io.Closeable;
import java.io.IOException;

/**
 * A minimal datagram endpoint.
 */
public interface DatagramEndpoint extends Closeable {

    void send(byte[] data) throws IOException;

    // blocks until a datagram is available
    byte[] receive() throws IOException;

    @Override
    void close();
}
