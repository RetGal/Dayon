package mpo.dayon.common.network.transport.ice;

import mpo.dayon.common.log.Log;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A reliable, ordered, multiplexed byte-stream protocol over a datagram endpoint, modelled on TCP
 * (SACK, delayed ACKs, Reno congestion control, per-stream flow control). Used because the JDK
 * ships neither QUIC nor DTLS/SCTP, and we want to avoid extra dependencies.
 * <p>
 * It does <b>not</b> provide confidentiality or authentication: {@link IceSessionTransport}
 * layers TLS on top of each stream.
 * <p>
 * Frame layout (20 byte header, big endian):
 * <pre>
 * magic(2) type(1) flags(1) stream(4) seq(4) ack(4) window(2) length(2) payload(length)
 * </pre>
 * The payload of an ACK frame is a selective acknowledgement bitmap: bit {@code i} set means
 * that segment {@code ack + 1 + i} has been received.
 */
public final class ReliableDatagramMultiplexer implements Closeable {

    private static final short MAGIC = (short) 0xD1CE;
    private static final byte TYPE_DATA = 1;
    private static final byte TYPE_ACK = 2;
    private static final byte TYPE_FIN = 3;

    private static final int HEADER_SIZE = 20;
    private static final int ACK_OFFSET = 12;
    private static final int WINDOW_OFFSET = 16;
    // keeps datagrams at or below 1200 bytes even inside a TURN Send indication (36 bytes)
    private static final int MAX_PAYLOAD = 1140;
    private static final int MAX_RECEIVE_BUFFER = 1 << 20;
    /** Upper bound for the congestion and receive windows (segments). */
    private static final int MAX_WINDOW = MAX_RECEIVE_BUFFER / MAX_PAYLOAD;
    private static final int INITIAL_CWND = 32;
    private static final int MIN_CWND = 4;
    // a segment is lost once segments this far above it have been selectively acknowledged
    private static final int DUP_THRESH = 3;
    // in-order segments are acknowledged every second segment; the timer acknowledges the rest
    private static final int ACK_EVERY = 2;
    private static final long MIN_RTO_MS = 250L;
    private static final long MAX_RTO_MS = 4_000L;
    private static final long RETRANSMIT_TICK_MS = 50L;
    private static final long KEEPALIVE_ACK_MS = 1_000L;
    private static final long IDLE_TIMEOUT_MS = 30_000L;
    private static final long LINGER_MS = 1_500L;

    private final DatagramEndpoint endpoint;
    private final Map<Integer, Stream> streams = new HashMap<>();
    // guards the protocol state - never held while sending, so a slow send cannot delay incoming ACKs
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition sendSpace = lock.newCondition();
    // serializes calls into the endpoint, which need not be thread-safe
    private final Object sendLock = new Object();
    private final ScheduledExecutorService timer;
    private final List<byte[]> receiverOutbox = new ArrayList<>();
    private final List<byte[]> timerOutbox = new ArrayList<>();

    private volatile boolean closed;
    private volatile boolean closing;
    private volatile long lastReceivedNanos = System.nanoTime();
    private Thread receiverThread;
    private ScheduledFuture<?> maintenanceTask;
    private ScheduledFuture<?> hardCloseTask;

    public ReliableDatagramMultiplexer(DatagramEndpoint endpoint) {
        this.endpoint = endpoint;
        this.timer = Executors.newSingleThreadScheduledExecutor(r -> {
            final Thread thread = new Thread(r, "DayonUdpTimer");
            thread.setDaemon(true);
            return thread;
        });
    }

    public synchronized void start() {
        if (receiverThread != null) {
            return;
        }
        receiverThread = new Thread(this::receiveLoop, "DayonUdpReceiver");
        receiverThread.setDaemon(true);
        receiverThread.start();
        maintenanceTask = timer.scheduleWithFixedDelay(this::maintenance, RETRANSMIT_TICK_MS, RETRANSMIT_TICK_MS, TimeUnit.MILLISECONDS);
    }

    public InputStream getInputStream(int streamId) {
        return new StreamInputStream(stream(streamId));
    }

    public OutputStream getOutputStream(int streamId) {
        return new StreamOutputStream(stream(streamId));
    }

    private Stream stream(int streamId) {
        lock.lock();
        try {
            return streams.computeIfAbsent(streamId, Stream::new);
        } finally {
            lock.unlock();
        }
    }

    private void receiveLoop() {
        try {
            while (!closed) {
                final byte[] datagram = endpoint.receive();
                if (datagram == null) {
                    break;
                }
                lastReceivedNanos = System.nanoTime();
                handleDatagram(datagram);
            }
        } catch (IOException ex) {
            if (!closed && !closing) {
                Log.debug("Reliable datagram receiver stopped: " + ex.getMessage());
            }
        } finally {
            failAll(new IOException("Datagram stream closed"));
            if (!closed) {
                hardClose();
            }
        }
    }

    private void handleDatagram(byte[] datagram) {
        if (datagram.length < HEADER_SIZE) {
            return;
        }
        final ByteBuffer buffer = ByteBuffer.wrap(datagram);
        if (buffer.getShort() != MAGIC) {
            return;
        }
        final byte type = buffer.get();
        buffer.get(); // flags (reserved)
        final int streamId = buffer.getInt();
        final int seq = buffer.getInt();
        final int ack = buffer.getInt();
        final int window = buffer.getShort() & 0xFFFF;
        final int length = buffer.getShort() & 0xFFFF;
        if (length > datagram.length - HEADER_SIZE) {
            return;
        }

        lock.lock();
        try {
            final Stream stream = streams.computeIfAbsent(streamId, Stream::new);
            final long now = System.nanoTime();
            handleAck(stream, ack, window, datagram, type == TYPE_ACK ? length : 0, now, receiverOutbox);
            if (type == TYPE_DATA) {
                handleData(stream, seq, datagram, length, now, receiverOutbox);
            } else if (type == TYPE_FIN) {
                handleFin(stream, seq, now, receiverOutbox);
            }
        } finally {
            lock.unlock();
        }
        flush(receiverOutbox);
    }

    private void handleAck(Stream stream, int ack, int peerWindow, byte[] datagram, int sackLength, long now, List<byte[]> out) {
        final boolean windowGrew = peerWindow > stream.peerWindow;
        stream.peerWindow = peerWindow;
        final int before = stream.unacked.size();
        while (!stream.unacked.isEmpty() && seqLess(stream.unacked.firstKey(), ack)) {
            stream.unacked.pollFirstEntry();
        }
        final boolean cumulativeProgress = stream.unacked.size() < before;
        final int highestSacked = sackLength > 0 ? applySack(stream, ack, datagram, sackLength) : ack;
        final int progress = before - stream.unacked.size();
        final int cwndBefore = stream.cwnd;
        if (progress > 0) {
            stream.rtoMs = MIN_RTO_MS;
            growWindow(stream, progress);
        }
        handleRecovery(stream, ack, now, out, highestSacked, cumulativeProgress, progress, cwndBefore);
        if (progress > 0 || windowGrew) {
            sendSpace.signalAll();
        }
        if (closing && stream.unacked.isEmpty()) {
            scheduleHardClose();
        }
    }

    private void handleRecovery(Stream stream, int ack, long now, List<byte[]> out, int highestSacked, boolean cumulativeProgress, int progress, int cwndBefore) {
        // RFC 6675 approximation: far enough below the highest SACKed segment means lost, not reordered
        int lossBound = highestSacked - DUP_THRESH + 1;
        if (stream.inRecovery && cumulativeProgress) {
            // NewReno: a partial ACK means the new first segment is lost too
            lossBound = latest(lossBound, ack + 1);
        }
        boolean entered = false;
        if (!stream.inRecovery) {
            if (!stream.unacked.isEmpty() && seqLess(stream.unacked.firstKey(), lossBound)) {
                enterRecovery(stream, now, lossBound, false);
                entered = true;
            }
        } else {
            stream.lossBound = latest(stream.lossBound, lossBound);
        }
        if (stream.inRecovery) {
            if (!seqLess(ack, stream.recoveryPoint)) {
                stream.inRecovery = false;
            } else {
                // packet conservation, plus slow-start growth after a timeout
                recover(stream, now, entered ? stream.cwnd : progress + stream.cwnd - cwndBefore, out);
            }
        }
    }

    /**
     * Drops SACKed segments; the receiver keeps acknowledged out-of-order data.
     *
     * @return the highest SACKed sequence number, or {@code ack} if there is none
     */
    private static int applySack(Stream stream, int ack, byte[] datagram, int length) {
        int highest = ack;
        for (int bit = 0; bit < length * 8; bit++) {
            if ((datagram[HEADER_SIZE + bit / 8] & 0xff & (1 << (bit % 8))) != 0) {
                final int seq = ack + 1 + bit;
                stream.unacked.remove(seq);
                highest = seq;
            }
        }
        return highest;
    }

    private static void growWindow(Stream stream, int acknowledged) {
        if (stream.inRecovery && !stream.timeoutRecovery) {
            return; // fast recovery keeps the halved window until the losses are repaired
        }
        if (stream.cwnd < stream.ssthresh) {
            stream.cwnd += acknowledged; // slow start
        } else {
            stream.cwndCredit += acknowledged; // congestion avoidance: +1 segment per window
            while (stream.cwndCredit >= stream.cwnd) {
                stream.cwndCredit -= stream.cwnd;
                stream.cwnd++;
            }
        }
        stream.cwnd = Math.min(stream.cwnd, MAX_WINDOW);
    }

    private static void enterRecovery(Stream stream, long now, int lossBound, boolean timeout) {
        stream.ssthresh = Math.max(stream.cwnd / 2, MIN_CWND);
        stream.cwnd = timeout ? MIN_CWND : stream.ssthresh;
        stream.cwndCredit = 0;
        stream.inRecovery = true;
        stream.timeoutRecovery = timeout;
        stream.recoveryPoint = stream.nextSeq;
        stream.recoveryStartNanos = now;
        stream.lossBound = lossBound;
    }

    // resends lost segments not yet resent since the recovery started
    private void recover(Stream stream, long now, int budget, List<byte[]> out) {
        int remaining = budget;
        for (Map.Entry<Integer, Segment> entry : stream.unacked.entrySet()) {
            if (remaining <= 0 || !seqLess(entry.getKey(), stream.lossBound)) {
                return;
            }
            final Segment segment = entry.getValue();
            if (segment.sentNanos - stream.recoveryStartNanos < 0) {
                out.add(retransmitFrame(stream, segment, now));
                remaining--;
            }
        }
    }

    private void handleData(Stream stream, int seq, byte[] datagram, int length, long now, List<byte[]> out) {
        if (seq == stream.expected) {
            stream.buffer.write(new Chunk(datagram, HEADER_SIZE, HEADER_SIZE + length));
            stream.expected++;
            final boolean filledGap = !stream.outOfOrder.isEmpty();
            drainOutOfOrder(stream);
            if (filledGap || ++stream.pendingAcks >= ACK_EVERY) {
                out.add(ackFrame(stream, now));
            }
            return;
        }
        if (!seqLess(seq, stream.expected) && seq - stream.expected <= MAX_WINDOW
                && !stream.outOfOrder.containsKey(seq)) {
            stream.outOfOrder.put(seq, new Chunk(datagram, HEADER_SIZE, HEADER_SIZE + length));
            stream.outOfOrderBytes += length;
        }
        // duplicate or out of order: acknowledge at once to report the gap
        out.add(ackFrame(stream, now));
    }

    private void handleFin(Stream stream, int seq, long now, List<byte[]> out) {
        if (seqLess(seq, stream.expected)) {
            stream.finReceived = true;
            stream.buffer.finish();
        } else if (seq == stream.expected) {
            stream.finReceived = true;
            stream.expected++;
            stream.buffer.finish();
        } else if (seq - stream.expected <= MAX_WINDOW) {
            stream.finSeq = seq;
        }
        out.add(ackFrame(stream, now));
    }

    private static void drainOutOfOrder(Stream stream) {
        while (true) {
            final Chunk next = stream.outOfOrder.remove(stream.expected);
            if (next == null) {
                break;
            }
            stream.outOfOrderBytes -= next.remaining();
            stream.buffer.write(next);
            stream.expected++;
        }
        if (stream.finSeq != null && !stream.finReceived && !seqLess(stream.expected, stream.finSeq)) {
            stream.finReceived = true;
            stream.expected = stream.finSeq + 1;
            stream.buffer.finish();
        }
    }

    // acknowledges progress or a reopened window; otherwise sends a periodic keep-alive ACK
    private void maybeSendAck(Stream stream, long now, List<byte[]> out) {
        if (stream.expected != stream.lastAckedExpected
                || (stream.lastAdvertisedWindow == 0 && receiveWindow(stream) > 0)
                || TimeUnit.NANOSECONDS.toMillis(now - stream.lastAckSentNanos) >= KEEPALIVE_ACK_MS) {
            out.add(ackFrame(stream, now));
        }
    }

    /**
     * @return the number of segments that still fit into the receive buffer
     */
    private static int receiveWindow(Stream stream) {
        final long free = MAX_RECEIVE_BUFFER - stream.buffer.available() - stream.outOfOrderBytes;
        return (int) Math.max(0, Math.min(MAX_WINDOW, free / MAX_PAYLOAD));
    }

    // every outgoing frame carries the current ACK and window, so it also counts as an ACK
    private static int advertise(Stream stream, long now) {
        final int window = receiveWindow(stream);
        stream.lastAckedExpected = stream.expected;
        stream.lastAckSentNanos = now;
        stream.lastAdvertisedWindow = window;
        stream.pendingAcks = 0;
        return window;
    }

    private static byte[] newFrame(byte type, Stream stream, int seq, int payloadLength, long now) {
        final byte[] frame = new byte[HEADER_SIZE + payloadLength];
        ByteBuffer.wrap(frame)
                .putShort(MAGIC)
                .put(type)
                .put((byte) 0)
                .putInt(stream.id)
                .putInt(seq)
                .putInt(stream.expected)
                .putShort((short) advertise(stream, now))
                .putShort((short) payloadLength);
        return frame;
    }

    private static byte[] ackFrame(Stream stream, long now) {
        final int sackLength = stream.outOfOrder.isEmpty() ? 0 : (stream.outOfOrder.lastKey() - stream.expected - 1) / 8 + 1;
        final byte[] frame = newFrame(TYPE_ACK, stream, 0, sackLength, now);
        for (int seq : stream.outOfOrder.keySet()) {
            final int bit = seq - stream.expected - 1;
            frame[HEADER_SIZE + bit / 8] |= (byte) (1 << (bit % 8));
        }
        return frame;
    }

    private static byte[] retransmitFrame(Stream stream, Segment segment, long now) {
        // patch a copy: another thread may still be sending the original
        final byte[] frame = segment.frame.clone();
        ByteBuffer.wrap(frame)
                .putInt(ACK_OFFSET, stream.expected)
                .putShort(WINDOW_OFFSET, (short) advertise(stream, now));
        segment.sentNanos = now;
        return frame;
    }

    // sends and clears the given frames - must be called without holding {@link #lock}
    private void flush(List<byte[]> frames) {
        for (byte[] frame : frames) {
            if (closed) {
                break;
            }
            try {
                synchronized (sendLock) {
                    endpoint.send(frame);
                }
            } catch (IOException ex) {
                if (!closed) {
                    Log.debug("Could not send datagram: " + ex.getMessage());
                }
            }
        }
        frames.clear();
    }

    private void maintenance() {
        if (closed) {
            return;
        }
        if (!closing && System.nanoTime() - lastReceivedNanos > TimeUnit.MILLISECONDS.toNanos(IDLE_TIMEOUT_MS)) {
            Log.warn("No data received for " + IDLE_TIMEOUT_MS + "ms - closing the datagram stream");
            hardClose();
            return;
        }
        lock.lock();
        try {
            final long now = System.nanoTime();
            for (Stream stream : streams.values()) {
                retransmitOnTimeout(stream, now, timerOutbox);
                maybeSendAck(stream, now, timerOutbox);
            }
        } finally {
            lock.unlock();
        }
        flush(timerOutbox);
    }

    // on RTO, presumes all outstanding segments lost and resends them within the collapsed window
    private void retransmitOnTimeout(Stream stream, long now, List<byte[]> out) {
        if (stream.unacked.isEmpty()) {
            return;
        }
        final Segment head = stream.unacked.firstEntry().getValue();
        if (now - head.sentNanos < TimeUnit.MILLISECONDS.toNanos(stream.rtoMs)) {
            return;
        }
        enterRecovery(stream, now, stream.nextSeq, true);
        stream.rtoMs = Math.min(stream.rtoMs * 2, MAX_RTO_MS);
        recover(stream, now, stream.cwnd, out);
    }

    private static boolean canSend(Stream stream) {
        // a closed peer window still allows one probe segment
        return stream.unacked.size() < Math.min(stream.cwnd, Math.max(1, stream.peerWindow));
    }

    private static boolean seqLess(int a, int b) {
        return a - b < 0;
    }

    private static int latest(int a, int b) {
        return seqLess(a, b) ? b : a;
    }

    @Override
    public void close() {
        final List<byte[]> out = new ArrayList<>();
        lock.lock();
        try {
            if (closed || closing) {
                return;
            }
            closing = true;
            final long now = System.nanoTime();
            for (Stream stream : streams.values()) {
                sendFin(stream, now, out);
            }
            sendSpace.signalAll();
        } finally {
            lock.unlock();
        }
        flush(out);
        scheduleHardClose();
    }

    // sends a FIN for one stream; the others continue
    public void closeStream(int streamId) {
        final List<byte[]> out = new ArrayList<>(1);
        lock.lock();
        try {
            if (closed) {
                return;
            }
            sendFin(streams.computeIfAbsent(streamId, Stream::new), System.nanoTime(), out);
        } finally {
            lock.unlock();
        }
        flush(out);
    }

    private static void sendFin(Stream stream, long now, List<byte[]> out) {
        if (stream.finSent) {
            return;
        }
        final int seq = stream.nextSeq++;
        final byte[] frame = newFrame(TYPE_FIN, stream, seq, 0, now);
        stream.unacked.put(seq, new Segment(frame, now));
        stream.finSent = true;
        out.add(frame);
    }

    private void scheduleHardClose() {
        if (hardCloseTask != null) {
            return;
        }
        hardCloseTask = timer.schedule(this::hardClose, LINGER_MS, TimeUnit.MILLISECONDS);
    }

    private void hardClose() {
        if (closed) {
            return;
        }
        closed = true;
        failAll(new EOFException("Datagram stream closed"));
        endpoint.close();
        if (maintenanceTask != null) {
            maintenanceTask.cancel(false);
        }
        if (hardCloseTask != null) {
            hardCloseTask.cancel(false);
        }
        timer.shutdownNow();
        lock.lock();
        try {
            sendSpace.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private void failAll(IOException error) {
        lock.lock();
        try {
            for (Stream stream : streams.values()) {
                stream.buffer.fail(error);
            }
        } finally {
            lock.unlock();
        }
    }

    // unacknowledged frame, header included
    private static final class Segment {
        final byte[] frame;
        long sentNanos;

        Segment(byte[] frame, long sentNanos) {
            this.frame = frame;
            this.sentNanos = sentNanos;
        }
    }

    private static final class Stream {
        final int id;
        final TreeMap<Integer, Segment> unacked = new TreeMap<>(Integer::compareUnsigned);
        final TreeMap<Integer, Chunk> outOfOrder = new TreeMap<>(Integer::compareUnsigned);
        final StreamBuffer buffer = new StreamBuffer();
        long outOfOrderBytes;
        int nextSeq;
        int expected;
        int peerWindow = MAX_WINDOW;
        // congestion control
        int cwnd = INITIAL_CWND;
        int ssthresh = MAX_WINDOW;
        int cwndCredit;
        boolean inRecovery;
        boolean timeoutRecovery;
        int recoveryPoint;
        int lossBound;
        long recoveryStartNanos;
        long rtoMs = MIN_RTO_MS;
        // stream end
        Integer finSeq;
        boolean finReceived;
        boolean finSent;
        // acknowledgements
        int pendingAcks;
        int lastAckedExpected;
        int lastAdvertisedWindow = -1;
        long lastAckSentNanos = System.nanoTime();

        Stream(int id) {
            this.id = id;
        }
    }

    // slice of a received datagram, avoiding a payload copy
    private static final class Chunk {
        final byte[] data;
        final int end;
        int offset;

        Chunk(byte[] data, int offset, int end) {
            this.data = data;
            this.offset = offset;
            this.end = end;
        }

        int remaining() {
            return end - offset;
        }
    }

    private static final class StreamBuffer {
        private final ArrayDeque<Chunk> chunks = new ArrayDeque<>();
        private long available;
        private boolean eof;
        private IOException error;

        synchronized void write(Chunk chunk) {
            if (chunk.remaining() > 0) {
                chunks.add(chunk);
                available += chunk.remaining();
            }
            notifyAll();
        }

        synchronized long available() {
            return available;
        }

        synchronized void finish() {
            eof = true;
            notifyAll();
        }

        synchronized void fail(IOException cause) {
            if (error == null) {
                error = cause;
            }
            eof = true;
            notifyAll();
        }

        synchronized int read(byte[] destination, int offset, int length) throws IOException {
            if (length == 0) {
                return 0;
            }
            while (available == 0 && !eof) {
                try {
                    wait();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while reading", ex);
                }
            }
            if (available == 0) {
                if (error != null) {
                    throw error;
                }
                return -1;
            }
            int written = 0;
            while (written < length && !chunks.isEmpty()) {
                final Chunk chunk = chunks.peek();
                final int count = Math.min(length - written, chunk.remaining());
                System.arraycopy(chunk.data, chunk.offset, destination, offset + written, count);
                chunk.offset += count;
                written += count;
                available -= count;
                if (chunk.remaining() == 0) {
                    chunks.poll();
                }
            }
            return written;
        }
    }

    private final class StreamInputStream extends InputStream {
        private final Stream stream;
        private final byte[] single = new byte[1];

        StreamInputStream(Stream stream) {
            this.stream = stream;
        }

        @Override
        public int read() throws IOException {
            final int count = read(single, 0, 1);
            return count < 0 ? -1 : single[0] & 0xFF;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return stream.buffer.read(b, off, len);
        }
    }

    private final class StreamOutputStream extends OutputStream {
        private final Stream stream;
        private final byte[] single = new byte[1];

        StreamOutputStream(Stream stream) {
            this.stream = stream;
        }

        @Override
        public void write(int b) throws IOException {
            single[0] = (byte) b;
            write(single, 0, 1);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            int position = off;
            final int end = off + len;
            while (position < end) {
                final List<byte[]> out = new ArrayList<>();
                lock.lock();
                try {
                    awaitSendSpace(stream);
                    final long now = System.nanoTime();
                    while (position < end && canSend(stream)) {
                        final int chunk = Math.min(MAX_PAYLOAD, end - position);
                        final int seq = stream.nextSeq++;
                        final byte[] frame = newFrame(TYPE_DATA, stream, seq, chunk, now);
                        System.arraycopy(b, position, frame, HEADER_SIZE, chunk);
                        stream.unacked.put(seq, new Segment(frame, now));
                        out.add(frame);
                        position += chunk;
                    }
                } finally {
                    lock.unlock();
                }
                ReliableDatagramMultiplexer.this.flush(out);
            }
        }

        @SuppressWarnings("squid:S899") // ignored because useless
        private void awaitSendSpace(Stream stream) throws IOException {
            while (true) {
                if (closing || closed) {
                    throw new IOException("Datagram stream closed");
                }
                if (canSend(stream)) {
                    return;
                }
                try {
                    sendSpace.await(100L, TimeUnit.MILLISECONDS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while writing", ex);
                }
            }
        }

    }
}
