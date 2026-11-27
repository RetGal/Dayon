package mpo.dayon.common.network.transport.ice;

import mpo.dayon.common.log.Log;
import mpo.dayon.common.network.transport.SessionTransport;
import org.ice4j.ice.Component;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static mpo.dayon.common.utils.SystemUtilities.safeClose;

/**
 * JDK TLS over a {@link ReliableDatagramSocket} on an established ICE component. The session uses
 * stream 0 and the file transfer stream 1, so both share a single (TURN) candidate pair.
 */
public final class IceSessionTransport implements SessionTransport {

    private static final int MAIN_STREAM = 0;
    private static final int FILE_STREAM = 1;
    private static final long HANDSHAKE_TIMEOUT_MS = 10_000L;

    private final Shared shared;
    private final SSLSocket socket;
    private final int streamId;
    private final boolean main;

    private volatile boolean started;
    private volatile boolean closed;

    private IceSessionTransport(Shared shared, SSLSocket socket, int streamId, boolean main) {
        this.shared = shared;
        this.socket = socket;
        this.streamId = streamId;
        this.main = main;
    }

    /**
     * @param clientMode true on the TLS client side (the assisted)
     * @return the transport once its TLS handshake has completed
     */
    public static IceSessionTransport open(Component component, SSLContext sslContext, boolean clientMode) throws IOException {
        final IceComponentEndpoint endpoint = new IceComponentEndpoint(component);
        final ReliableDatagramMultiplexer multiplexer = new ReliableDatagramMultiplexer(endpoint);
        multiplexer.start();
        final Shared shared = new Shared(endpoint, multiplexer, sslContext);
        final IceSessionTransport transport = createChannel(shared, MAIN_STREAM, clientMode, true);
        transport.handshake();
        return transport;
    }

    private static IceSessionTransport createChannel(Shared shared, int streamId, boolean clientMode, boolean main) {
        final ReliableDatagramSocket raw = new ReliableDatagramSocket(
                shared.multiplexer,
                streamId,
                shared.remoteAddress,
                shared.remotePort,
                main ? shared::closeAll : () -> shared.multiplexer.closeStream(streamId));
        try {
            final SSLSocket ssl = (SSLSocket) shared.sslContext.getSocketFactory()
                    .createSocket(raw, shared.remoteHost, shared.remotePort, true);
            ssl.setUseClientMode(clientMode);
            if (!clientMode) {
                ssl.setNeedClientAuth(true);
            }
            final IceSessionTransport transport = new IceSessionTransport(shared, ssl, streamId, main);
            shared.channels.add(transport);
            return transport;
        } catch (IOException ex) {
            raw.close();
            throw new IllegalStateException("Could not create the TLS layer over the ICE stream", ex);
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

    // bounded, because a peer not using the datagram path never answers
    private synchronized void handshake() throws IOException {
        if (started) {
            return;
        }
        if (closed) {
            throw new IOException("The ICE session channel is closed");
        }
        final AtomicBoolean timedOut = new AtomicBoolean(false);
        final CompletableFuture<Void> watchdog = CompletableFuture.runAsync(() -> {
            if (!started) {
                timedOut.set(true);
                close();
            }
        }, CompletableFuture.delayedExecutor(HANDSHAKE_TIMEOUT_MS, TimeUnit.MILLISECONDS));
        try {
            Log.debug("Starting TLS over ICE [stream:" + streamId + ", client:" + socket.getUseClientMode() + "]");
            socket.startHandshake();
            started = true;
        } catch (IOException ex) {
            close();
            if (timedOut.get()) {
                throw new SocketTimeoutException("TLS handshake over ICE timed out after " + HANDSHAKE_TIMEOUT_MS + "ms");
            }
            throw ex;
        } finally {
            watchdog.cancel(false);
        }
    }

    @Override
    public boolean isConnected() {
        return !closed && socket.isConnected() && !socket.isClosed();
    }

    @Override
    public InetAddress getRemoteAddress() {
        return shared.remoteAddress;
    }

    @Override
    public SSLSession getSslSession() {
        return socket.getSession();
    }

    // preserve the TLS role of the main stream, so both peers can call this
    @Override
    public SessionTransport openFileChannel() throws IOException {
        final IceSessionTransport channel = fileChannel(socket.getUseClientMode());
        channel.handshake();
        return channel;
    }

    private synchronized IceSessionTransport fileChannel(boolean clientMode) throws IOException {
        if (shared.closed.get()) {
            throw new IOException("The ICE session is already closed");
        }
        if (shared.fileChannel == null) {
            shared.fileChannel = createChannel(shared, FILE_STREAM, clientMode, false);
        }
        return shared.fileChannel;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (main) {
            shared.closeAll();
        } else {
            safeClose(socket);
        }
    }

    private static final class Shared {
        final ReliableDatagramMultiplexer multiplexer;
        final SSLContext sslContext;
        final InetAddress remoteAddress;
        final int remotePort;
        final String remoteHost;
        final Set<IceSessionTransport> channels = ConcurrentHashMap.newKeySet();
        final AtomicBoolean closed = new AtomicBoolean(false);

        volatile IceSessionTransport fileChannel;

        Shared(IceComponentEndpoint endpoint, ReliableDatagramMultiplexer multiplexer, SSLContext sslContext) {
            this.multiplexer = multiplexer;
            this.sslContext = sslContext;
            final InetAddress address = endpoint.getRemoteAddress();
            this.remoteAddress = address;
            this.remotePort = endpoint.getRemotePort();
            this.remoteHost = address == null ? "dayon-peer" : address.getHostAddress();
        }

        void closeAll() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            for (IceSessionTransport channel : channels) {
                channel.closed = true;
                safeClose(channel.socket);
            }
            // sends FIN frames, so the peer does not have to wait for the idle timeout
            multiplexer.close();
        }
    }
}
