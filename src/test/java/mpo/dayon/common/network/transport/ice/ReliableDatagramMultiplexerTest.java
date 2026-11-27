package mpo.dayon.common.network.transport.ice;

import mpo.dayon.common.security.CustomTrustManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@Timeout(15)
class ReliableDatagramMultiplexerTest {

    private final InMemoryEndpoint first = new InMemoryEndpoint();
    private final InMemoryEndpoint second = new InMemoryEndpoint();

    private ReliableDatagramMultiplexer firstMux;
    private ReliableDatagramMultiplexer secondMux;

    @AfterEach
    void tearDown() {
        if (firstMux != null) {
            firstMux.close();
        }
        if (secondMux != null) {
            secondMux.close();
        }
        first.close();
        second.close();
    }

    @Test
    void transfersAByteStreamOverSeveralDatagrams() throws Exception {
        connect();

        final byte[] payload = randomBytes(8_000);
        final OutputStream out = firstMux.getOutputStream(0);
        out.write(payload);
        out.flush();

        assertArrayEquals(payload, readFully(secondMux.getInputStream(0), payload.length));
    }

    @Test
    void keepsLogicalStreamsIndependent() throws Exception {
        connect();

        final byte[] control = randomBytes(3_000);
        final byte[] file = randomBytes(5_000);
        firstMux.getOutputStream(0).write(control);
        firstMux.getOutputStream(1).write(file);

        assertArrayEquals(control, readFully(secondMux.getInputStream(0), control.length));
        assertArrayEquals(file, readFully(secondMux.getInputStream(1), file.length));
    }

    @Test
    void recoversFromLostDatagrams() throws Exception {
        connect();

        final byte[] payload = randomBytes(4_000);
        first.dropNextPackets(3);
        firstMux.getOutputStream(0).write(payload);

        assertArrayEquals(payload, readFully(secondMux.getInputStream(0), payload.length));
    }

    @Test
    void resendsOnlyTheLostSegment() throws Exception {
        connect();

        final int segments = 20;
        final byte[] payload = randomBytes(segments * 1140);
        first.dropDataFrame(5);
        firstMux.getOutputStream(0).write(payload);

        assertArrayEquals(payload, readFully(secondMux.getInputStream(0), payload.length));
        assertEquals(segments + 1, first.dataFramesSent(), "only the lost segment should have been resent");
    }

    @Test
    void transfersALargePayloadDespitePeriodicLoss() throws Exception {
        connect();

        // larger than the receive buffer, so that flow control is exercised as well
        final byte[] payload = randomBytes(3 * 1024 * 1024);
        first.dropEveryDataFrame(25);
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            final Future<byte[]> received = executor.submit(() -> readFully(secondMux.getInputStream(0), payload.length));
            firstMux.getOutputStream(0).write(payload);
            assertArrayEquals(payload, received.get(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void reassemblesOutOfOrderDatagrams() throws Exception {
        connect();

        final byte[] payload = randomBytes(2_500);
        first.holdNextPacket();
        firstMux.getOutputStream(0).write(payload);

        assertTrue(first.hasHeldPacket(), "the first datagram should have been held back");
        first.releaseHeldPackets();

        assertArrayEquals(payload, readFully(secondMux.getInputStream(0), payload.length));
    }

    @Test
    void signalsTheEndOfAStream() throws Exception {
        connect();

        final InputStream in = secondMux.getInputStream(0);
        firstMux.closeStream(0);

        assertEquals(-1, in.read());
    }

    /**
     * The whole point of the multiplexer: the JDK TLS implementation can run unchanged on top
     * of the reliable datagram stream. This is what carries the existing object-stream session.
     */
    @Test
    void runsTlsOverTheReliableStream() throws Exception {
        connect();

        final InetAddress loopback = InetAddress.getLoopbackAddress();
        final ReliableDatagramSocket rawClient = new ReliableDatagramSocket(
                firstMux, 0, loopback, 1234, () -> firstMux.closeStream(0));
        final ReliableDatagramSocket rawServer = new ReliableDatagramSocket(
                secondMux, 0, loopback, 4321, () -> secondMux.closeStream(0));

        final SSLContext context = CustomTrustManager.initSslContext(true);
        final SSLSocket client = (SSLSocket) context.getSocketFactory()
                .createSocket(rawClient, loopback.getHostAddress(), 1234, true);
        client.setUseClientMode(true);
        final SSLSocket server = (SSLSocket) context.getSocketFactory()
                .createSocket(rawServer, loopback.getHostAddress(), 4321, true);
        server.setUseClientMode(false);
        server.setNeedClientAuth(true);

        final ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            final Future<?> serverHandshake = executor.submit(() -> {
                server.startHandshake();
                return null;
            });
            client.startHandshake();
            serverHandshake.get(10, TimeUnit.SECONDS);

            client.getOutputStream().write("ping".getBytes(StandardCharsets.UTF_8));
            client.getOutputStream().flush();
            assertEquals("ping", new String(readFully(server.getInputStream(), 4), StandardCharsets.UTF_8));

            server.getOutputStream().write("pong".getBytes(StandardCharsets.UTF_8));
            server.getOutputStream().flush();
            assertEquals("pong", new String(readFully(client.getInputStream(), 4), StandardCharsets.UTF_8));
        } finally {
            executor.shutdownNow();
            client.close();
            server.close();
        }
    }

    private void connect() {
        first.connect(second);
        second.connect(first);
        firstMux = new ReliableDatagramMultiplexer(first);
        secondMux = new ReliableDatagramMultiplexer(second);
        firstMux.start();
        secondMux.start();
    }

    private static byte[] readFully(InputStream in, int length) throws IOException {
        final byte[] data = new byte[length];
        int read = 0;
        while (read < length) {
            final int count = in.read(data, read, length - read);
            if (count < 0) {
                fail("unexpected end of stream after " + read + " of " + length + " bytes");
            }
            read += count;
        }
        return data;
    }

    private static byte[] randomBytes(int length) {
        final byte[] data = new byte[length];
        new Random(42).nextBytes(data);
        return data;
    }

    /**
     * An in-memory {@link DatagramEndpoint} pair with hooks to drop, hold back and delay
     * datagrams so that the reliability behaviour can be tested deterministically.
     */
    private static final class InMemoryEndpoint implements DatagramEndpoint {

        private final BlockingQueue<byte[]> incoming = new LinkedBlockingQueue<>();
        private final List<byte[]> held = new ArrayList<>();
        private volatile InMemoryEndpoint peer;
        private volatile boolean closed;
        private int dropCount;
        private boolean holdNext;
        private int dataFrames;
        private int dropDataFrame;
        private int dropEveryDataFrame;

        void connect(InMemoryEndpoint other) {
            peer = other;
        }

        synchronized void dropNextPackets(int count) {
            dropCount = count;
        }

        synchronized void dropDataFrame(int number) {
            dropDataFrame = number;
        }

        synchronized void dropEveryDataFrame(int interval) {
            dropEveryDataFrame = interval;
        }

        synchronized int dataFramesSent() {
            return dataFrames;
        }

        private boolean dropDataFrame(byte[] data) {
            if (data[2] != 1) {
                return false;
            }
            dataFrames++;
            return dataFrames == dropDataFrame
                    || (dropEveryDataFrame > 0 && dataFrames % dropEveryDataFrame == 0);
        }

        synchronized void holdNextPacket() {
            holdNext = true;
        }

        synchronized void releaseHeldPackets() {
            for (byte[] datagram : held) {
                peer.incoming.offer(datagram);
            }
            held.clear();
        }

        synchronized boolean hasHeldPacket() {
            return !held.isEmpty();
        }

        @Override
        public void send(byte[] data) throws IOException {
            final InMemoryEndpoint target = peer;
            if (target == null || closed) {
                throw new IOException("endpoint closed");
            }
            synchronized (this) {
                if (dropDataFrame(data)) {
                    return;
                }
                if (dropCount > 0) {
                    dropCount--;
                    return;
                }
                if (holdNext) {
                    holdNext = false;
                    held.add(data.clone());
                    return;
                }
            }
            target.incoming.offer(data.clone());
        }

        @Override
        public byte[] receive() throws IOException {
            while (!closed) {
                try {
                    final byte[] datagram = incoming.poll(100, TimeUnit.MILLISECONDS);
                    if (datagram != null) {
                        return datagram;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrupted", e);
                }
            }
            throw new IOException("endpoint closed");
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
