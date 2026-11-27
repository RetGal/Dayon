package mpo.dayon.common.network.transport;

import mpo.dayon.common.log.Log;

import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import static mpo.dayon.common.utils.SystemUtilities.safeClose;

/**
 * The classic Dayon! transport: a TLS connection over TCP.
 */
public final class TcpSessionTransport implements SessionTransport {

    private final SSLSocket socket;
    private final SSLSocketFactory socketFactory;
    private final String remoteHost;
    private final int remotePort;
    private final int connectTimeoutMs;
    private final int soTimeoutMs;

    private TcpSessionTransport(SSLSocket socket, SSLSocketFactory socketFactory, String remoteHost, int remotePort,
                                int connectTimeoutMs, int soTimeoutMs) {
        this.socket = socket;
        this.socketFactory = socketFactory;
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
        this.connectTimeoutMs = connectTimeoutMs;
        this.soTimeoutMs = soTimeoutMs;
        disableNagle(socket);
    }

    // connects as a TLS client and returns once the socket is ready to be used
    public static TcpSessionTransport connect(SSLSocketFactory socketFactory, String host, int port,
                                              int connectTimeoutMs, int soTimeoutMs) throws IOException {
        try (SocketCloseGuard guard = new SocketCloseGuard((SSLSocket) socketFactory.createSocket())) {
            final SSLSocket socket = guard.getSocket();
            socket.setNeedClientAuth(true);
            socket.setSoTimeout(soTimeoutMs);
            socket.connect(new InetSocketAddress(host, port), connectTimeoutMs);
            // once connected, remain connected until cancelled
            socket.setSoTimeout(0);
            final TcpSessionTransport transport =
                    new TcpSessionTransport(socket, socketFactory, host, port, connectTimeoutMs, soTimeoutMs);
            guard.release();
            return transport;
        } catch (UnknownHostException e) {
            Log.warn("Unable to connect - unknown host");
            throw e;
        } catch (SocketTimeoutException e) {
            Log.warn("Unable to connect - connection timeout");
            throw e;
        } catch (IOException e) {
            Log.warn("Unable to connect");
            throw e;
        }
    }

    private static final class SocketCloseGuard implements AutoCloseable {
        private final SSLSocket socket;
        private boolean released;

        private SocketCloseGuard(SSLSocket socket) {
            this.socket = socket;
        }

        private SSLSocket getSocket() {
            return socket;
        }

        private void release() {
            released = true;
        }

        @Override
        public void close() {
            if (!released) {
                safeClose(socket);
            }
        }
    }

    // wraps an already connected (accepted) TLS socket
    public static TcpSessionTransport of(SSLSocket socket) {
        return new TcpSessionTransport(socket, null, null, 0, 0, 0);
    }

    private static void disableNagle(SSLSocket socket) {
        try {
            socket.setTcpNoDelay(true);
        } catch (IOException ex) {
            Log.warn("Could not disable Nagle on the connection", ex);
        }
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return socket.getInputStream();
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        return socket.getOutputStream();
    }

    @Override
    public boolean isConnected() {
        return socket.isConnected() && !socket.isClosed();
    }

    @Override
    public InetAddress getRemoteAddress() {
        return socket.getInetAddress();
    }

    @Override
    public SSLSession getSslSession() {
        return socket.getSession();
    }

    @Override
    public SessionTransport openFileChannel() throws IOException {
        if (socketFactory == null) {
            throw new IOException("An accepted connection cannot open the file channel");
        }
        return connect(socketFactory, remoteHost, remotePort, connectTimeoutMs, soTimeoutMs);
    }

    @Override
    public void close() {
        safeClose(socket);
    }
}
