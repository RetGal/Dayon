package mpo.dayon.assistant.network;

import mpo.dayon.assisted.capture.CaptureEngineConfiguration;
import mpo.dayon.assisted.compressor.CompressorEngineConfiguration;
import mpo.dayon.common.concurrent.RunnableEx;
import mpo.dayon.common.configuration.ReConfigurable;
import mpo.dayon.common.event.Listeners;
import mpo.dayon.common.log.Log;
import mpo.dayon.common.network.NetworkEngine;
import mpo.dayon.common.network.NetworkSender;
import mpo.dayon.common.network.message.*;
import mpo.dayon.common.version.Version;

import javax.net.ssl.*;
import java.awt.datatransfer.ClipboardOwner;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.String.format;
import static mpo.dayon.common.network.message.NetworkMessageType.CLIPBOARD_FILES;
import static mpo.dayon.common.network.message.NetworkMessageType.PING;
import static mpo.dayon.common.utils.SystemUtilities.*;
import static mpo.dayon.common.version.Version.isCompatibleVersion;

public class NetworkAssistantEngine extends NetworkEngine implements ReConfigurable<NetworkAssistantConfiguration> {

    private final NetworkCaptureMessageHandler captureMessageHandler;

    private final NetworkMouseLocationMessageHandler mouseMessageHandler;

    private final ClipboardOwner clipboardOwner;

    private final Listeners<NetworkAssistantEngineListener> listeners = new Listeners<>();

    private NetworkAssistantConfiguration configuration;

    private SSLServerSocketFactory ssf;

    private Thread receiver; // in

    private NetworkSender sender; // out

    private ServerSocket server;

    private Socket connection;

    private ObjectInputStream in;

    private Thread fileReceiver; // file in

    private NetworkSender fileSender; // file out

    private ServerSocket fileServer;

    private Socket fileConnection;

    private ObjectInputStream fileIn;

    private final AtomicBoolean cancelling = new AtomicBoolean(false);

    private static final String LOCALHOST = "127.0.0.1";

    private int port;

    public NetworkAssistantEngine(NetworkCaptureMessageHandler captureMessageHandler, NetworkMouseLocationMessageHandler mouseMessageHandler, ClipboardOwner clipboardOwner) {
        this.captureMessageHandler = captureMessageHandler;
        this.mouseMessageHandler = mouseMessageHandler;
        this.clipboardOwner = clipboardOwner;
        fireOnReady();
    }

    @Override
    public void configure(NetworkAssistantConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public void reconfigure(NetworkAssistantConfiguration configuration) {
        this.configuration = configuration;
    }

    public void addListener(NetworkAssistantEngineListener listener) {
        listeners.add(listener);
    }

    public String getLocalhost() {
        return LOCALHOST;
    }

    /**
     * Called from a GUI action => do not block the AWT thread (!)
     */
    public void start() {
        if (cancelling.get() || receiver != null) {
            return;
        }
        port = configuration.getPort();

        receiver = new Thread(new RunnableEx() {
            @Override
            protected void doRun() throws Exception {
                NetworkAssistantEngine.this.receivingLoop();
            }
        }, "NetworkReceiver");

        receiver.start();
    }

    /**
     * Called from a GUI action => do not block the AWT thread (!)
     */
    public void cancel() {
        Log.info("Cancelling the network assistant engine...");

        cancelling.set(true);
        safeClose(server, connection, fileServer, fileConnection);
        fireOnDisconnecting();
    }

    @java.lang.SuppressWarnings("squid:S2189")
    private void receivingLoop() throws NoSuchAlgorithmException, KeyManagementException {
        in = null;

        try {
            awaitConnections();
            startFileReceiver();
            sender = new NetworkSender(new ObjectOutputStream(new BufferedOutputStream(connection.getOutputStream())));
            sender.start(8);
            sender.ping();

            in = initInputStream();
            boolean introduced = false;

            //noinspection InfiniteLoopStatement
            while (true) {
                NetworkMessage.unmarshallMagicNumber(in); // blocking read (!)
                NetworkMessageType type = NetworkMessage.unmarshallEnum(in, NetworkMessageType.class);
                Log.debug(format("Received %s", type.name()));

                if (introduced) {
                    processIntroduced(type, in);
                } else {
                    introduced = processUnIntroduced(type, in);
                }
            }
        } catch (IOException ex) {
            handleIOException(ex);
        } finally {
            closeConnections();
            fireOnReady();
        }

    }

    private void awaitConnections() throws NoSuchAlgorithmException, IOException, KeyManagementException {
        fireOnStarting(port);

        ssf = initSSLContext().getServerSocketFactory();
        Log.info(format("Dayon! server [port:%d]", port));
        server = ssf.createServerSocket(port);
        Log.info("Accepting ...");

        do {
            safeClose(connection); // we might have refused the accepted connection (!)
            connection = server.accept();
            Log.info(format("Incoming connection from %s", connection.getInetAddress().getHostAddress()));
        } while (!fireOnAccepted(connection) && !cancelling.get());

        server.close();
        server = null;
    }

    private ObjectInputStream initInputStream() throws IOException {
        try {
            return new ObjectInputStream(new BufferedInputStream(connection.getInputStream()));
        } catch (StreamCorruptedException ex) {
            throw new IOException("version.wrong");
        }
    }

    private void startFileReceiver() {
        fileReceiver = new Thread(new RunnableEx() {
            @Override
            protected void doRun() {
                NetworkAssistantEngine.this.fileReceivingLoop();
            }
        }, "FileReceiver");

        fileReceiver.start();
    }

    // right, keep streams open - forever!
    @java.lang.SuppressWarnings({"squid:S2189", "squid:S2093"})
    private void fileReceivingLoop() {
        fileIn = null;
        Log.info(format("Dayon! file server [port:%d]", port));

        try {
            fileServer = ssf.createServerSocket(port);
            fileConnection = fileServer.accept();

            fileSender = new NetworkSender(new ObjectOutputStream(new BufferedOutputStream(fileConnection.getOutputStream())));
            fileSender.start(1);
            fileSender.ping();

            fileIn = new ObjectInputStream(new BufferedInputStream(fileConnection.getInputStream()));

            String tmpDir = getTempDir();
            NetworkClipboardFilesHelper filesHelper = new NetworkClipboardFilesHelper();

            //noinspection InfiniteLoopStatement
            while (true) {
                NetworkMessageType type;
                if (filesHelper.isDone()) {
                    NetworkMessage.unmarshallMagicNumber(fileIn); // blocking read (!)
                    type = NetworkMessage.unmarshallEnum(fileIn, NetworkMessageType.class);
                    Log.debug("Received " + type.name());
                } else {
                    type = CLIPBOARD_FILES;
                }

                if (type.equals(CLIPBOARD_FILES)) {
                    filesHelper = handleNetworkClipboardFilesHelper(NetworkClipboardFilesMessage.unmarshall(fileIn,
                            filesHelper, tmpDir), clipboardOwner);
                    if (filesHelper.isDone()) {
                        fireOnClipboardReceived();
                    }
                } else if (!type.equals(PING)) {
                    throw new IllegalArgumentException(format(UNSUPPORTED_TYPE, type));
                }
            }
        } catch (IOException ex) {
            closeConnections();
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

    private void processIntroduced(NetworkMessageType type, ObjectInputStream in) throws IOException {
        switch (type) {
            case CAPTURE:
                final NetworkCaptureMessage capture = NetworkCaptureMessage.unmarshall(in);
                fireOnByteReceived(1 + capture.getWireSize()); // +1 : magic number (byte)
                captureMessageHandler.handleCapture(capture);
                break;

            case MOUSE_LOCATION:
                final NetworkMouseLocationMessage mouse = NetworkMouseLocationMessage.unmarshall(in);
                fireOnByteReceived(1 + mouse.getWireSize()); // +1 : magic number (byte)
                mouseMessageHandler.handleLocation(mouse);
                break;

            case CLIPBOARD_TEXT:
                final NetworkClipboardTextMessage clipboardTextMessage = NetworkClipboardTextMessage.unmarshall(in);
                fireOnByteReceived(1 + clipboardTextMessage.getWireSize()); // +1 : magic number (byte)
                setClipboardContents(clipboardTextMessage.getText(), clipboardOwner);
                fireOnClipboardReceived();
                break;

            case PING:
                fireOnClipboardSent();
                break;

            case RESIZE:
                final NetworkResizeScreenMessage resize = NetworkResizeScreenMessage.unmarshall(in);
                fireOnByteReceived(1 + resize.getWireSize()); // +1 : magic number (byte)
                fireOnResizeScreen(resize.getWidth(), resize.getHeigth());
                break;

            case HELLO:
                throw new IllegalArgumentException("Unexpected message [HELLO]!");

            default:
                throw new IllegalArgumentException(format(UNSUPPORTED_TYPE, type));
        }
    }

    private boolean processUnIntroduced(NetworkMessageType type, ObjectInputStream in) throws IOException {
        switch (type) {
            case HELLO:
                introduce(in);
                fireOnConnected(connection);
                return true;

            case PING:
                return false;

            case CAPTURE:
            case MOUSE_LOCATION:
            case CLIPBOARD_TEXT:
            case CLIPBOARD_FILES:
                throw new IllegalArgumentException(format("Unexpected message [%s]!", type.name()));

            default:
                throw new IllegalArgumentException(format(UNSUPPORTED_TYPE, type));
        }
    }

    private void introduce(ObjectInputStream in) throws IOException {
        final NetworkHelloMessage hello = NetworkHelloMessage.unmarshall(in);
        fireOnByteReceived(1 + hello.getWireSize()); // +1 : magic number (byte)
        if (!isCompatibleVersion(hello.getMajor(), hello.getMinor(), Version.get())) {
            Log.error(format("Incompatible assisted version: %d.%d", hello.getMajor(), hello.getMinor()));
            throw new IOException("version.wrong");
        }
    }

    private void closeConnections() {
        if (sender != null) {
            sender.cancel();
        }
        receiver = safeInterrupt(receiver);
        safeClose(in, connection, server);

        if (fileSender != null) {
            fileSender.cancel();
        }
        fileReceiver = safeInterrupt(fileReceiver);
        safeClose(fileIn, fileConnection, fileServer);

        cancelling.set(false);
    }


    /**
     * Might be blocking if the sender queue is full (!)
     */
    public void sendCaptureConfiguration(CaptureEngineConfiguration configuration) {
        if (sender != null) {
            sender.sendCaptureConfiguration(configuration);
        }
    }

    /**
     * Might be blocking if the sender queue is full (!)
     */
    public void sendCompressorConfiguration(CompressorEngineConfiguration configuration) {
        if (sender != null) {
            sender.sendCompressorConfiguration(configuration);
        }
    }

    /**
     * Might be blocking if the sender queue is full (!)
     */
    public void sendMouseControl(NetworkMouseControlMessage message) {
        if (sender != null) {
            sender.sendMouseControl(message);
        }
    }

    /**
     * Might be blocking if the sender queue is full (!)
     */
    public void sendKeyControl(NetworkKeyControlMessage message) {
        if (sender != null) {
            sender.sendKeyControl(message);
        }
    }

    /**
     * Might be blocking if the sender queue is full (!)
     */
    public void sendRemoteClipboardRequest() {
        if (sender != null) {
            sender.sendRemoteClipboardRequest();
        }
    }

    /**
     * Might be blocking if the sender queue is full (!)
     */
    public void setRemoteClipboardText(String text, int size) {
        if (sender != null) {
            sender.sendClipboardContentText(text, size);
        }
    }

    /**
     * Might be blocking if the sender queue is full (!)
     */
    public void setRemoteClipboardFiles(List<File> files, long size, String basePath) {
        if (fileSender != null) {
            fileSender.sendClipboardContentFiles(files, size, basePath);
        }
    }

    private void fireOnReady() {
        for (final NetworkAssistantEngineListener xListener : listeners.getListeners()) {
            xListener.onReady();
        }
    }

    private void fireOnStarting(int port) {
        for (final NetworkAssistantEngineListener xListener : listeners.getListeners()) {
            xListener.onStarting(port);
        }
    }

    private boolean fireOnAccepted(Socket connection) {
        return listeners.getListeners().stream().allMatch(xListener -> xListener.onAccepted(connection));
    }

    private void fireOnConnected(Socket connection) {
        for (final NetworkAssistantEngineListener xListener : listeners.getListeners()) {
            xListener.onConnected(connection);
        }
    }

    private void fireOnByteReceived(int count) {
        for (final NetworkAssistantEngineListener xListener : listeners.getListeners()) {
            xListener.onByteReceived(count);
        }
    }

    private void fireOnClipboardReceived() {
        for (final NetworkAssistantEngineListener xListener : listeners.getListeners()) {
            xListener.onClipboardReceived();
        }
    }

    private void fireOnClipboardSent() {
        for (final NetworkAssistantEngineListener xListener : listeners.getListeners()) {
            xListener.onClipboardSent();
        }
    }

    private void fireOnResizeScreen(int width, int height) {
        for (final NetworkAssistantEngineListener xListener : listeners.getListeners()) {
            xListener.onResizeScreen(width, height);
        }
    }

    private void fireOnDisconnecting() {
        for (final NetworkAssistantEngineListener xListener : listeners.getListeners()) {
            xListener.onDisconnecting();
        }
    }

    private void fireOnIOError(IOException error) {
        for (final NetworkAssistantEngineListener xListener : listeners.getListeners()) {
            xListener.onIOError(error);
        }
    }
}
