package mpo.dayon.assisted.network;

import mpo.dayon.assisted.compressor.CompressorEngineConfiguration;
import mpo.dayon.assisted.compressor.CompressorEngineListener;
import mpo.dayon.assisted.control.NetworkControlMessageHandler;
import mpo.dayon.assisted.mouse.MouseEngineListener;
import mpo.dayon.common.buffer.MemByteBuffer;
import mpo.dayon.common.capture.Capture;
import mpo.dayon.common.concurrent.RunnableEx;
import mpo.dayon.common.configuration.Configurable;
import mpo.dayon.common.error.FatalErrorHandler;
import mpo.dayon.common.event.Listeners;
import mpo.dayon.common.log.Log;
import mpo.dayon.common.network.NetworkEngine;
import mpo.dayon.common.network.NetworkSender;
import mpo.dayon.common.network.message.*;
import mpo.dayon.common.squeeze.CompressionMethod;

import javax.net.ssl.*;
import java.awt.*;
import java.awt.datatransfer.ClipboardOwner;
import java.io.*;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static mpo.dayon.common.network.message.NetworkMessageType.CLIPBOARD_FILES;
import static mpo.dayon.common.network.message.NetworkMessageType.PING;
import static mpo.dayon.common.utils.SystemUtilities.*;

public class NetworkAssistedEngine extends NetworkEngine
        implements Configurable<NetworkAssistedEngineConfiguration>, CompressorEngineListener, MouseEngineListener {
    private NetworkAssistedEngineConfiguration configuration;

    private final NetworkCaptureConfigurationMessageHandler captureConfigurationHandler;

    private final NetworkCompressorConfigurationMessageHandler compressorConfigurationHandler;

    private final NetworkControlMessageHandler controlHandler;

    private final NetworkClipboardRequestMessageHandler clipboardRequestHandler;

    private final ClipboardOwner clipboardOwner;

    private final Listeners<NetworkAssistedEngineListener> listeners = new Listeners<>();

    private Thread receiver; // in

    private NetworkSender sender; // out

    private ObjectInputStream in;

    private Thread fileReceiver; // file in

    private NetworkSender fileSender; // file out

    private ObjectInputStream fileIn;

    private final AtomicBoolean cancelling = new AtomicBoolean(false);

    public NetworkAssistedEngine(NetworkCaptureConfigurationMessageHandler captureConfigurationHandler,
                                 NetworkCompressorConfigurationMessageHandler compressorConfigurationHandler,
                                 NetworkControlMessageHandler controlHandler,
                                 NetworkClipboardRequestMessageHandler clipboardRequestHandler, ClipboardOwner clipboardOwner) {
        this.captureConfigurationHandler = captureConfigurationHandler;
        this.compressorConfigurationHandler = compressorConfigurationHandler;
        this.controlHandler = controlHandler;
        this.clipboardRequestHandler = clipboardRequestHandler;
        this.clipboardOwner = clipboardOwner;
    }

    public NetworkAssistedEngineConfiguration getConfiguration() {
        return configuration;
    }

    private void runReceivers() {
        this.receiver = new Thread(new RunnableEx() {
            @Override
            protected void doRun() {
                NetworkAssistedEngine.this.receivingLoop();
            }
        }, "CommandReceiver");

        this.fileReceiver = new Thread(new RunnableEx() {
            @Override
            protected void doRun() {
                NetworkAssistedEngine.this.fileReceivingLoop();
            }
        }, "FileReceiver");
    }

    @Override
    public void configure(NetworkAssistedEngineConfiguration configuration) {
        Log.info("New configuration (configuration)");
        this.configuration = configuration;
    }

    public void addListener(NetworkAssistedEngineListener listener) {
        listeners.add(listener);
    }

    public void connect() {
        try {
            start();
            sendHello();
            fireOnConnected();
        } catch (UnknownHostException e) {
            fireOnHostNotFound(configuration);
        } catch (SocketTimeoutException e) {
            fireOnConnectionTimeout(configuration);
        } catch (IOException e) {
            closeConnections();
            fireOnRefused(configuration);
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            FatalErrorHandler.bye(e.getMessage(), e);
        }
    }

    @SuppressWarnings("java:S2095") // our sockets MUST NOT be closed
    private void start() throws IOException, NoSuchAlgorithmException, KeyManagementException {
        Log.info("Connecting to [" + configuration.getServerName() + "][" + configuration.getServerPort() + "]...");
        fireOnConnecting(configuration);

        if (receiver == null) {
            Log.info("Getting the receivers ready");
            runReceivers();
        }

        SSLSocketFactory ssf = initSSLContext().getSocketFactory();
        SSLSocket connection = (SSLSocket) ssf.createSocket(configuration.getServerName(), configuration.getServerPort());
        sender = new NetworkSender(new ObjectOutputStream(new BufferedOutputStream(connection.getOutputStream()))); // the active part (!)
        sender.start(1);
        sender.ping();
        in = initInputStream(connection);
        receiver.start();

        SSLSocket fileConnection = (SSLSocket) ssf.createSocket(configuration.getServerName(), configuration.getServerPort());
        fileSender = new NetworkSender(new ObjectOutputStream(new BufferedOutputStream(fileConnection.getOutputStream()))); // the active part (!)
        fileSender.start(1);
        fileSender.ping();
        fileIn = new ObjectInputStream(new BufferedInputStream(fileConnection.getInputStream()));
        fileReceiver.start();
    }

    /**
     * Called from a GUI action => do not block the AWT thread (!)
     */
    public void cancel() {
        Log.info("Cancelling the network assisted engine...");
        cancelling.set(true);
        closeConnections();
        fireOnDisconnecting();
    }

    private ObjectInputStream initInputStream(SSLSocket connection) throws IOException {
        try {
            return new ObjectInputStream(new BufferedInputStream(connection.getInputStream()));
        } catch (StreamCorruptedException ex) {
            throw new IOException("version.wrong");
        }
    }

    private void receivingLoop() {

        try {
            //noinspection InfiniteLoopStatement
            while (true) {

                NetworkMessage.unmarshallMagicNumber(in); // blocking read (!)
                NetworkMessageType type = NetworkMessage.unmarshallEnum(in, NetworkMessageType.class);

                switch (type) {
                    case CAPTURE_CONFIGURATION:
                        final NetworkCaptureConfigurationMessage captureConfigurationMessage = NetworkCaptureConfigurationMessage.unmarshall(in);
                        captureConfigurationHandler.handleConfiguration(captureConfigurationMessage);
                        break;

                    case COMPRESSOR_CONFIGURATION:
                        final NetworkCompressorConfigurationMessage compressorConfigurationMessage = NetworkCompressorConfigurationMessage.unmarshall(in);
                        compressorConfigurationHandler.handleConfiguration(compressorConfigurationMessage);
                        break;

                    case MOUSE_CONTROL:
                        final NetworkMouseControlMessage mouseControlMessage = NetworkMouseControlMessage.unmarshall(in);
                        controlHandler.handleMessage(mouseControlMessage);
                        break;

                    case KEY_CONTROL:
                        final NetworkKeyControlMessage keyControlMessage = NetworkKeyControlMessage.unmarshall(in);
                        controlHandler.handleMessage(keyControlMessage);
                        break;

                    case CLIPBOARD_REQUEST:
                        clipboardRequestHandler.handleClipboardRequest();
                        break;

                    case CLIPBOARD_TEXT:
                        final NetworkClipboardTextMessage clipboardTextMessage = NetworkClipboardTextMessage.unmarshall(in);
                        setClipboardContents(clipboardTextMessage.getText(), clipboardOwner);
                        sender.ping();
                        break;

                    case PING:
                        break;

                    default:
                        throw new IllegalArgumentException(String.format(UNSUPPORTED_TYPE, type));
                }
            }
        } catch (IOException ex) {
            handleIOException(ex);
        } finally {
            closeConnections();
            fireOnDisconnecting();
        }
    }

    private void handleIOException(IOException ex) {
        if (!cancelling.get()) {
            Log.error("IO error (not cancelled)", ex);
            fireOnIOError(ex);
        } else {
            Log.info("Stopped network receiver (cancelled)");
        }
    }

    public void closeConnections() {
        if (sender != null) {
            sender.cancel();
        }
        receiver = safeInterrupt(receiver);
        safeClose(in);

        if (fileSender != null) {
            fileSender.cancel();
        }
        fileReceiver = safeInterrupt(fileReceiver);
        safeClose(fileIn);

        cancelling.set(false);
    }

    private void fileReceivingLoop() {

        NetworkClipboardFilesHelper filesHelper = new NetworkClipboardFilesHelper();
        String tmpDir = getTempDir();

        try {
            //noinspection InfiniteLoopStatement
            while (true) {
                NetworkMessageType type;
                if (filesHelper.isDone()) {
                    NetworkMessage.unmarshallMagicNumber(fileIn); // blocking read (!)
                    type = NetworkMessage.unmarshallEnum(fileIn, NetworkMessageType.class);
                    Log.debug("Received " + type.name());
                } else {
                    type = NetworkMessageType.CLIPBOARD_FILES;
                }

                if (type.equals(CLIPBOARD_FILES)) {
                    filesHelper = handleNetworkClipboardFilesHelper(NetworkClipboardFilesMessage.unmarshall(fileIn,
                            filesHelper, tmpDir), clipboardOwner);
                    if (filesHelper.isDone()) {
                        // let the assistant know that we're done
                        sender.ping();
                    }
                } else if (!type.equals(PING)) {
                    throw new IllegalArgumentException(String.format(UNSUPPORTED_TYPE, type));
                }
            }

        } catch (IOException ex) {
            closeConnections();
        }
    }

    /**
     * The first message being sent to the assistant (e.g., version
     * identification).
     */
    public void sendHello() {
        if (sender != null) {
            sender.sendHello();
        }
    }

    /**
     * May block (!)
     * <p/>
     * We're receiving a fully compressed (and ready to send over the network)
     * capture.
     */
    @Override
    public void onCompressed(Capture capture, CompressionMethod compressionMethod, CompressorEngineConfiguration compressionConfiguration,
                             MemByteBuffer compressed) {
        if (sender != null) {
            sender.sendCapture(capture, compressionMethod, compressionConfiguration, compressed);
        }
    }

    /**
     * May block (!)
     */
    @Override
    public boolean onLocationUpdated(Point location) {
        return sender == null || sender.sendMouseLocation(location);
    }

    public void sendClipboardText(String text, int size) {
        if (sender != null) {
            sender.sendClipboardContentText(text, size);
        }
    }

    public void sendClipboardFiles(List<File> files, long size, String basePath) {
        if (fileSender != null) {
            fileSender.sendClipboardContentFiles(files, size, basePath);
        }
    }

    public void sendResizeScreen(int width, int height) {
        if (sender != null) {
            sender.sendResizeScreen(width, height);
        }
    }

    private void fireOnConnecting(NetworkAssistedEngineConfiguration configuration) {
        for (final NetworkAssistedEngineListener xListener : listeners.getListeners()) {
            xListener.onConnecting(configuration.getServerName(), configuration.getServerPort());
        }
    }

    private void fireOnHostNotFound(NetworkAssistedEngineConfiguration configuration) {
        for (final NetworkAssistedEngineListener xListener : listeners.getListeners()) {
            xListener.onHostNotFound(configuration.getServerName());
        }
    }

    private void fireOnConnectionTimeout(NetworkAssistedEngineConfiguration configuration) {
        for (final NetworkAssistedEngineListener xListener : listeners.getListeners()) {
            xListener.onConnectionTimeout(configuration.getServerName(), configuration.getServerPort());
        }
    }

    private void fireOnRefused(NetworkAssistedEngineConfiguration configuration) {
        for (final NetworkAssistedEngineListener xListener : listeners.getListeners()) {
            xListener.onRefused(configuration.getServerName(), configuration.getServerPort());
        }
    }

    private void fireOnConnected() {
        for (final NetworkAssistedEngineListener xListener : listeners.getListeners()) {
            xListener.onConnected();
        }
    }

    private void fireOnDisconnecting() {
        for (final NetworkAssistedEngineListener xListener : listeners.getListeners()) {
            xListener.onDisconnecting();
        }
    }

    private void fireOnIOError(IOException ex) {
        for (final NetworkAssistedEngineListener xListener : listeners.getListeners()) {
            xListener.onIOError(ex);
        }
    }
}
