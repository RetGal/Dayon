package mpo.dayon.assistant.network;

import mpo.dayon.assistant.network.https.NetworkAssistantHttpsEngine;
import mpo.dayon.assistant.network.https.NetworkAssistantHttpsResources;
import mpo.dayon.assisted.capture.CaptureEngineConfiguration;
import mpo.dayon.assisted.compressor.CompressorEngineConfiguration;
import mpo.dayon.common.concurrent.RunnableEx;
import mpo.dayon.common.configuration.ReConfigurable;
import mpo.dayon.common.event.Listeners;
import mpo.dayon.common.log.Log;
import mpo.dayon.common.network.NetworkEngine;
import mpo.dayon.common.network.NetworkSender;
import mpo.dayon.common.network.message.*;
import mpo.dayon.common.security.CustomTrustManager;
import mpo.dayon.common.version.Version;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.TrustManager;
import java.awt.datatransfer.ClipboardOwner;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static mpo.dayon.common.network.message.NetworkMessageType.CLIPBOARD_FILES;
import static mpo.dayon.common.network.message.NetworkMessageType.PING;
import static mpo.dayon.common.security.CustomTrustManager.KEY_STORE_PASS;
import static mpo.dayon.common.security.CustomTrustManager.KEY_STORE_PATH;
import static mpo.dayon.common.utils.SystemUtilities.safeClose;

public class NetworkAssistantEngine extends NetworkEngine implements ReConfigurable<NetworkAssistantConfiguration> {

    private final NetworkCaptureMessageHandler captureMessageHandler;

    private final NetworkMouseLocationMessageHandler mouseMessageHandler;

    private final ClipboardOwner clipboardOwner;

    private final Listeners<NetworkAssistantEngineListener> listeners = new Listeners<>();

    private NetworkAssistantConfiguration configuration;

    private Thread receiver; // in

    private NetworkSender sender; // out

    private ServerSocket server;

    private Socket connection;

    private ObjectInputStream in;

    private ObjectOutputStream out;

    private Thread fileReceiver; // file in

    private NetworkSender fileSender; // file out

    private ServerSocket fileServer;

    private Socket fileConnection;

    private ObjectInputStream fileIn;

    private ObjectOutputStream fileOut;

    private final AtomicBoolean cancelling = new AtomicBoolean(false);

    private NetworkAssistantHttpsEngine https;

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

    public int getPort() {
        return configuration.getPort();
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

        if (https != null) {
            https.cancel();
            https = null;
        }

        //noinspection StatementWithEmptyBody
        while (server == null && connection == null) {
            // waiting for Godot (jetty may not have finished starting up)
        }

        safeClose(server);
        safeClose(connection);
        safeClose(fileServer);
        safeClose(fileConnection);
        fireOnDisconnecting();
    }

    @java.lang.SuppressWarnings("squid:S2189")
    private void receivingLoop() throws KeyStoreException, NoSuchAlgorithmException, CertificateException, UnrecoverableKeyException, KeyManagementException {
        in = null;
        out = null;

        try {
            Log.info(String.format("HTTPS server [port:%d]", port));
            fireOnHttpStarting(port);

            NetworkAssistantHttpsResources.setup(LOCALHOST, port); // JNLP support (.html, .jnlp, .jar)

            https = new NetworkAssistantHttpsEngine(port);
            https.start(); // blocking call until the HTTP-acceptor has been closed (!)

            Log.info(String.format("Dayon! server [port:%d]", port));
            fireOnStarting(port);

            server = initServerSocket(port);

            Log.info("Accepting ...");
            fireOnAccepting(port);

            if (https != null) {
                https.onDayonAccepting();
            }

            do {
                safeClose(connection); // we might have refused the accepted connection (!)
                connection = server.accept();
                Log.info(String.format("Incoming connection from %s", connection.getInetAddress().getHostAddress()));
            } while (!fireOnAccepted(connection) && !cancelling.get());

            server.close();
            server = null;

            startFileReceiver();

            out = new ObjectOutputStream(new BufferedOutputStream(connection.getOutputStream()));

            sender = new NetworkSender(out);
            sender.start(8);
            sender.ping();

            in = new ObjectInputStream(new BufferedInputStream(connection.getInputStream()));

            boolean introduced = false;

            //noinspection InfiniteLoopStatement
            while (true) {

                NetworkMessage.unmarshallMagicNumber(in); // blocking read (!)
                NetworkMessageType type = NetworkMessage.unmarshallEnum(in, NetworkMessageType.class);
                Log.debug("Received " + type.name());

                if (introduced) {
                    processIntroduced(type, in);
                } else {
                    introduced = processUnIntroduced(type, in);
                }
            }
        } catch (IOException ex) {
            handleIOException(ex);
            closeConnections();
        }

        fireOnReady();
    }

    private void startFileReceiver() {
        fileReceiver = new Thread(new RunnableEx() {
            @Override
            protected void doRun() throws Exception {
                NetworkAssistantEngine.this.fileReceivingLoop();
            }
        }, "FileReceiver");

        fileReceiver.start();
    }

    @java.lang.SuppressWarnings("squid:S2189")
    private void fileReceivingLoop() throws KeyStoreException, NoSuchAlgorithmException, CertificateException, UnrecoverableKeyException, KeyManagementException {
        fileIn = null;
        fileOut = null;

        try {
            Log.info(String.format("Dayon! file server [port:%d]", port));

            fileServer = initServerSocket(port);
            fileConnection = fileServer.accept();

            fileOut = new ObjectOutputStream(new BufferedOutputStream(fileConnection.getOutputStream()));

            fileSender = new NetworkSender(fileOut);
            fileSender.start(1);
            fileSender.ping();

            fileIn = new ObjectInputStream(new BufferedInputStream(fileConnection.getInputStream()));

            NetworkClipboardFilesHelper filesHelper = new NetworkClipboardFilesHelper();

            //noinspection InfiniteLoopStatement
            while (true) {

                NetworkMessageType type;
                if (filesHelper.isIdle()) {
                    NetworkMessage.unmarshallMagicNumber(fileIn); // blocking read (!)
                    type = NetworkMessage.unmarshallEnum(fileIn, NetworkMessageType.class);
                    Log.debug("Received " + type.name());
                } else {
                    type = CLIPBOARD_FILES;
                }

                if (type.equals(CLIPBOARD_FILES)) {
                    filesHelper = processClipboardFiles(fileIn, filesHelper);
                } else if (!type.equals(PING)) {
                    throw new IllegalArgumentException(String.format(UNSUPPORTED_TYPE, type));
                }

            }
        } catch (IOException ex) {
            handleIOException(ex);
            closeConnections();
        }

        fireOnReady();
    }

    private NetworkClipboardFilesHelper processClipboardFiles(ObjectInputStream in, NetworkClipboardFilesHelper filesHelper) throws IOException {
        final NetworkClipboardFilesMessage clipboardFiles = NetworkClipboardFilesMessage.unmarshall(in, filesHelper);
        fireOnByteReceived(1 + clipboardFiles.getWireSize()); // +1 : magic number (byte)
        filesHelper = handleNetworkClipboardFilesHelper(filesHelper, clipboardFiles, clipboardOwner);
        if (filesHelper.isIdle()) {
            fireOnClipboardReceived();
        }
        return filesHelper;
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

            case HELLO:
                throw new IllegalArgumentException("Unexpected message [HELLO]!");

            default:
                throw new IllegalArgumentException(String.format(UNSUPPORTED_TYPE, type));
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
                throw new IllegalArgumentException("Unexpected message [" + type.name() + "]!");

            default:
                throw new IllegalArgumentException(String.format(UNSUPPORTED_TYPE, type));
        }
    }

    private void introduce(ObjectInputStream in) throws IOException {
        if (https != null) {
            https.cancel();
            https = null;
        }

        final NetworkHelloMessage hello = NetworkHelloMessage.unmarshall(in);
        fireOnByteReceived(1 + hello.getWireSize()); // +1 : magic number (byte)

        final Version version = Version.get();
        final boolean isProd = isProd(version, hello.getMajor(), hello.getMinor());

        if (isProd && (version.getMajor() != hello.getMajor() || version.getMinor() != hello.getMinor())) {
            throw new IOException("Version Error!");
        }
    }

    private void closeConnections() {
        if (sender != null) {
            sender.cancel();
        }
        if (fileSender != null) {
            fileSender.cancel();
        }
        if (receiver != null) {
            receiver.interrupt();
            receiver = null;
        }
        if (fileReceiver != null) {
            fileReceiver.interrupt();
            fileReceiver = null;
        }

        safeClose(in);
        safeClose(out);
        safeClose(connection);
        safeClose(server);
        safeClose(fileIn);
        safeClose(fileOut);
        safeClose(fileConnection);
        safeClose(fileServer);

        cancelling.set(false);
    }

    private ServerSocket initServerSocket(int port) throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException, UnrecoverableKeyException, KeyManagementException {
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(NetworkAssistantHttpsEngine.class.getResourceAsStream(KEY_STORE_PATH), KEY_STORE_PASS.toCharArray());

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        try {
            kmf.init(keyStore, KEY_STORE_PASS.toCharArray());
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(kmf.getKeyManagers(), new TrustManager[]{new CustomTrustManager()}, new SecureRandom());
            SSLServerSocketFactory ssf = sslContext.getServerSocketFactory();
            return ssf.createServerSocket(port);
        } catch (KeyManagementException | UnrecoverableKeyException e) {
            Log.error("Fatal, can not init encryption", e);
            throw e;
        }
    }

    private static boolean isProd(Version version, int major, int minor) {
        return !(version.isNull() || (major == 0 && minor == 0));
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
    public void setRemoteClipboardFiles(List<File> files, long size) {
        if (fileSender != null) {
            fileSender.sendClipboardContentFiles(files, size);
        }
    }

    private void fireOnReady() {
        for (final NetworkAssistantEngineListener xListener : listeners.getListeners()) {
            xListener.onReady();
        }
    }

    private void fireOnHttpStarting(int port) {
        for (final NetworkAssistantEngineListener xListener : listeners.getListeners()) {
            xListener.onHttpStarting(port);
        }
    }

    private void fireOnStarting(int port) {
        for (final NetworkAssistantEngineListener xListener : listeners.getListeners()) {
            xListener.onStarting(port);
        }
    }

    private void fireOnAccepting(int port) {
        for (final NetworkAssistantEngineListener xListener : listeners.getListeners()) {
            xListener.onAccepting(port);
        }
    }

    private boolean fireOnAccepted(Socket connection) {
        for (final NetworkAssistantEngineListener xListener : listeners.getListeners()) {
            if (!xListener.onAccepted(connection)) {
                return false;
            }
        }
        return true;
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
