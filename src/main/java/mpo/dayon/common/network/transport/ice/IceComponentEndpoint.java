package mpo.dayon.common.network.transport.ice;

import mpo.dayon.common.log.Log;
import org.ice4j.TransportAddress;
import org.ice4j.ice.Component;
import org.ice4j.socket.MultiplexingDatagramSocket;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;

/**
 * A {@link DatagramEndpoint} backed by an established ICE component. Sends explicitly target the
 * selected remote candidate, which works for host, reflexive and relayed (TURN) candidates alike.
 */
public final class IceComponentEndpoint implements DatagramEndpoint {

    private static final int MAX_DATAGRAM = 64 * 1024;

    private final String mediaStreamName;
    private final Component component;
    private final MultiplexingDatagramSocket socket;
    private final InetSocketAddress remoteAddress;
    private final byte[] receiveBuffer = new byte[MAX_DATAGRAM];

    private volatile boolean closed;

    public IceComponentEndpoint(Component component) {
        this.component = component;
        this.mediaStreamName = component.getParentStream().getName();
        this.socket = component.getSocket();
        if (socket == null) {
            throw new IllegalArgumentException("The ICE component socket is disabled (ice4j.use-component-socket)");
        }
        final TransportAddress remote = selectedRemoteAddress(component);
        this.remoteAddress = new InetSocketAddress(remote.getAddress(), remote.getPort());
    }

    public InetAddress getRemoteAddress() {
        return remoteAddress.getAddress();
    }

    public int getRemotePort() {
        return remoteAddress.getPort();
    }

    private static TransportAddress selectedRemoteAddress(Component component) {
        if (component.getSelectedPair() != null) {
            return component.getSelectedPair().getRemoteCandidate().getTransportAddress();
        }
        throw new IllegalStateException("ICE component has no selected pair");
    }

    @Override
    public void send(byte[] data) throws IOException {
        if (closed) {
            throw new SocketException("Endpoint closed");
        }
        final DatagramPacket packet = new DatagramPacket(data, data.length);
        packet.setSocketAddress(remoteAddress);
        socket.send(packet);
    }

    @Override
    public byte[] receive() throws IOException {
        // called by the single receiver thread of the multiplexer, so the buffer can be reused
        final DatagramPacket packet = new DatagramPacket(receiveBuffer, receiveBuffer.length);
        socket.receive(packet);
        final byte[] data = new byte[packet.getLength()];
        System.arraycopy(packet.getData(), packet.getOffset(), data, 0, packet.getLength());
        return data;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            socket.close();
        } catch (RuntimeException ex) {
            Log.debug("Could not close the ICE component socket: " + ex.getMessage());
        }
        Log.debug("Closed ICE component endpoint [" + mediaStreamName + ":" + component.getComponentID() + "]");
    }
}
