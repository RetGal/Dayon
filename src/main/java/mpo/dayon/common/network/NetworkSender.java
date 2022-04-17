package mpo.dayon.common.network;

import java.awt.Point;
import java.io.File;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import mpo.dayon.common.network.message.*;

import mpo.dayon.assisted.capture.CaptureEngineConfiguration;
import mpo.dayon.assisted.compressor.CompressorEngineConfiguration;
import mpo.dayon.common.buffer.MemByteBuffer;
import mpo.dayon.common.capture.Capture;
import mpo.dayon.common.concurrent.DefaultThreadFactoryEx;
import mpo.dayon.common.concurrent.Executable;
import mpo.dayon.common.error.FatalErrorHandler;
import mpo.dayon.common.squeeze.CompressionMethod;
import mpo.dayon.common.version.Version;

public class NetworkSender {
    private final ObjectOutputStream out;

    private ThreadPoolExecutor executor;

    private Semaphore semaphore;

    public NetworkSender(ObjectOutputStream out) {
        this.out = out;
    }

    /**
     * The compressor engine has merged (into its internal queue) captures
     * waiting to be sent over the network. So I guess the queue-size should not
     * be too big as we would sent old captures for nothing - think about the
     * mouse location messages as well ...
     */
    public void start(int queueSize) {
        // THREAD = 1
        //
        // We're serializing access to the output stream (i.e., socket) (!)
        executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
        executor.setThreadFactory(new DefaultThreadFactoryEx("NetworkSender"));
        semaphore = new Semaphore(queueSize, true);
    }

    public void cancel() {
        executor.shutdownNow();
    }

    /**
     * Might block (!)
     * <p/>
     * Assisted 2 assistant.
     */
    public void sendHello() {
        final Version version = Version.get();
        send(true, new NetworkHelloMessage(version.getMajor(), version.getMinor()));
    }

    /**
     * Might block (!)
     * <p/>
     * Assisted 2 assistant.
     */
    public void sendCapture(Capture capture, CompressionMethod compressionMethod, CompressorEngineConfiguration compressionConfiguration,
                            MemByteBuffer compressed) {
        send(true, new NetworkCaptureMessage(capture.getId(), compressionMethod, compressionConfiguration, compressed));
    }

    /**
     * Might block (!)
     * <p/>
     * Assisted 2 assistant.
     */
    public boolean sendMouseLocation(Point location) {
        // No point to buffer old location of the mouse - here the mouse engine
        // is directly connected
        // to that post (i.e., there'no intermediate queue as for the capture
        // engine and its compressor
        // engine in between).

        if (!semaphore.tryAcquire()) {
            return false;
        }
        send(false, new NetworkMouseLocationMessage(location.x, location.y));
        return true;
    }

    /**
     * Might block (!)
     * <p/>
     * Assistant 2 assisted.
     */
    public void sendCaptureConfiguration(CaptureEngineConfiguration configuration) {
        send(true, new NetworkCaptureConfigurationMessage(configuration));
    }

    /**
     * Might block (!)
     * <p/>
     * Assistant 2 assisted.
     */
    public void sendCompressorConfiguration(CompressorEngineConfiguration configuration) {
        send(true, new NetworkCompressorConfigurationMessage(configuration));
    }

    /**
     * Might block (!)
     * <p/>
     * Assistant 2 assisted.
     */
    public void sendMouseControl(NetworkMouseControlMessage message) {
        send(true, message);
    }

    /**
     * Might block (!)
     * <p/>
     * Assistant 2 assisted.
     */
    public void sendKeyControl(NetworkKeyControlMessage message) {
        send(true, message);
    }

    /**
     * Might block (!)
     * <p/>
     * Assistant 2 assisted .
     */
    public void sendRemoteClipboardRequest() {
        send(true, new NetworkClipboardRequestMessage());
    }

    /**
     * Might block (!)
     * <p/>
     * Assistant 2 assisted or vice versa.
     */
    public void sendClipboardContentText(String text, int size) {
        final NetworkMessage message = new NetworkClipboardTextMessage(text, size);
        send(true, message);
    }

    /**
     * Might block (!)
     * <p/>
     * Assistant 2 assisted or vice versa.
     */
    public void sendClipboardContentFiles(List<File> files, long size, String basePath) {
        final NetworkMessage message = new NetworkClipboardFilesMessage(files, size, basePath);
        send(true, message);
    }

    public void sendResizeScreen(int width, int height) {
        final NetworkMessage message = new NetworkResizeScreenMessage(width, height);
        send(true, message);
    }

    public void ping() {
        send(false, new NetworkPingMessage());
    }

    private void send(boolean acquireSemaphore, NetworkMessage message) {
        try {
            if (acquireSemaphore) {
                semaphore.acquire();
            }
            executor.execute(new MyExecutable(executor, semaphore, out, message));
        } catch (RejectedExecutionException ex) {
            semaphore.release(); // unlikely as we have an unbounded queue
            // (!)
        } catch (InterruptedException ex) {
            FatalErrorHandler.bye("The [" + Thread.currentThread().getName() + "] thread is has been interrupted!", ex);
            Thread.currentThread().interrupt();
        }
    }

    private static class MyExecutable extends Executable {
        private final ObjectOutputStream out;
        private final NetworkMessage message;

        MyExecutable(ExecutorService executor, Semaphore semaphore, ObjectOutputStream out, NetworkMessage message) {
            super(executor, semaphore);
            this.out = out;
            this.message = message;
        }

        @Override
        protected void execute() throws IOException {
            NetworkMessage.marshallMagicNumber(out);
            message.marshall(out);
            out.flush();
        }

    }

}
