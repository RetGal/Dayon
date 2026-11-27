package mpo.dayon.common.network.transport.ice;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;

/**
 * A {@link Socket} over one stream of a {@link ReliableDatagramMultiplexer}, so that JSSE can layer
 * TLS on top. Only the methods used by SunJSSE are implemented.
 */
final class ReliableDatagramSocket extends Socket {

    private final InputStream in;
    private final OutputStream out;
    private final InetAddress remoteAddress;
    private final int remotePort;
    private final Runnable closeAction;

    private volatile boolean closed;

    ReliableDatagramSocket(ReliableDatagramMultiplexer multiplexer, int streamId,
                           InetAddress remoteAddress, int remotePort, Runnable closeAction) {
        this.in = multiplexer.getInputStream(streamId);
        this.out = multiplexer.getOutputStream(streamId);
        this.remoteAddress = remoteAddress;
        this.remotePort = remotePort;
        this.closeAction = closeAction;
    }

    @Override
    public InputStream getInputStream() {
        return in;
    }

    @Override
    public OutputStream getOutputStream() {
        return out;
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        closeAction.run();
        try {
            in.close();
            out.close();
        } catch (IOException ignored) {
            // the underlying streams are closed with the multiplexer
        }
    }

    @Override
    public boolean isConnected() {
        return !closed;
    }

    @Override
    public boolean isBound() {
        return !closed;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public InetAddress getInetAddress() {
        return remoteAddress;
    }

    @Override
    public int getPort() {
        return remotePort;
    }

    @Override
    public InetAddress getLocalAddress() {
        return remoteAddress;
    }

    @Override
    public int getLocalPort() {
        return 0;
    }

    @Override
    public SocketAddress getRemoteSocketAddress() {
        return new InetSocketAddress(remoteAddress, remotePort);
    }

    @Override
    public SocketAddress getLocalSocketAddress() {
        return new InetSocketAddress(remoteAddress, 0);
    }

    @Override
    public void setSoTimeout(int timeout) {
        // timeouts are enforced by the reliable datagram layer
    }

    @Override
    public int getSoTimeout() {
        return 0;
    }

    @Override
    public void setTcpNoDelay(boolean on) {
        // not applicable to a datagram transport
    }

    @Override
    public void setKeepAlive(boolean on) {
        // ICE keep-alives are handled by ice4j
    }

    @Override
    public void setReuseAddress(boolean on) {
        // not applicable
    }

    @Override
    public void shutdownInput() {
        // no half-close support on a datagram stream
    }

    @Override
    public void shutdownOutput() {
        // no half-close support on a datagram stream
    }

    @Override
    public String toString() {
        return "ReliableDatagramSocket[" + (remoteAddress == null ? "?" : remoteAddress.getHostAddress()) + ":" + remotePort + "]";
    }
}
