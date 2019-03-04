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
import mpo.dayon.common.utils.SystemUtilities;
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

import static mpo.dayon.common.security.CustomTrustManager.KEY_STORE_PASS;
import static mpo.dayon.common.security.CustomTrustManager.KEY_STORE_PATH;

public class NetworkAssistantEngine extends NetworkEngine implements ReConfigurable<NetworkAssistantConfiguration> {

    private final NetworkCaptureMessageHandler captureMessageHandler;

    private final NetworkMouseLocationMessageHandler mouseMessageHandler;

    private final ClipboardOwner clipboardOwner;

    private final Listeners<NetworkAssistantEngineListener> listeners = new Listeners<>();

    private NetworkAssistantConfiguration configuration;

    /**
     * IN.
     */
    private Thread receiver;

    /**
     * OUT.
     */
    private NetworkSender sender;

    private ServerSocket server;

    private Socket connection;

    private final AtomicBoolean cancelling = new AtomicBoolean(false);

    private NetworkAssistantHttpsEngine https;

    private static final String LOCALHOST = "127.0.0.1";

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
     * Possibly called from a GUI action => do not block the AWT thread (!)
     */
    @Override
    public void start() {
        if (cancelling.get() || receiver != null) {
            return;
        }

        receiver = new Thread(new RunnableEx() {
            @Override
            protected void doRun() throws Exception {
                NetworkAssistantEngine.this.receivingLoop();
            }
        }, "NetworkReceiver");

        receiver.start();
    }

    /**
     * Possibly called from a GUI action => do not block the AWT thread (!)
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

        SystemUtilities.safeClose(server);
        SystemUtilities.safeClose(connection);
    }

    private void receivingLoop() throws KeyStoreException, NoSuchAlgorithmException, CertificateException {
        ObjectInputStream in = null;
        ObjectOutputStream out = null;

        try {
            final int port = getPort();

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
                SystemUtilities.safeClose(connection); // we might have refused the accepted connection (!)
                connection = server.accept();
                Log.info(String.format("Incoming connection from %s", connection.getInetAddress().getHostAddress()));
            } while (!fireOnAccepted(connection) && !cancelling.get());

            server.close();
            server = null;

            out = new ObjectOutputStream(new BufferedOutputStream(connection.getOutputStream()));

            sender = new NetworkSender(out);
            sender.start(8);
            sender.ping();

            in = new ObjectInputStream(new BufferedInputStream(connection.getInputStream()));

            boolean introduced = false;

            NetworkClipboardFilesHelper filesHelper = new NetworkClipboardFilesHelper();

            //noinspection InfiniteLoopStatement
            while (true) {

                NetworkMessageType type;
                if (filesHelper.isIdle()) {
                    NetworkMessage.unmarshallMagicNumber(in); // blocking read (!)
                    type = NetworkMessage.unmarshallEnum(in, NetworkMessageType.class);
                } else {
                    type = NetworkMessageType.CLIPBOARD_FILES;
                }
                Log.debug("Received " + type.name());

                switch (type) {
                    case HELLO:
                        if (introduced) {
                            throw new IOException("Unexpected message [HELLO]!");
                        }

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

                        introduced = true;
                        fireOnConnected(connection);
                        break;

                    case CAPTURE:
                        if (!introduced) {
                            throw new IOException("Unexpected message [CAPTURE]!");
                        }

                        final NetworkCaptureMessage capture = NetworkCaptureMessage.unmarshall(in);
                        fireOnByteReceived(1 + capture.getWireSize()); // +1 : magic number (byte)

                        captureMessageHandler.handleCapture(capture);
                        break;

                    case MOUSE_LOCATION:
                        if (!introduced) {
                            throw new IOException("Unexpected message [MOUSE_LOCATION]!");
                        }

                        final NetworkMouseLocationMessage mouse = NetworkMouseLocationMessage.unmarshall(in);
                        fireOnByteReceived(1 + mouse.getWireSize()); // +1 : magic number (byte)

                        mouseMessageHandler.handleLocation(mouse);
                        break;

                    case CLIPBOARD_TEXT:
                        if (!introduced) {
                            throw new IOException("Unexpected message [CLIPBOARD_TEXT]!");
                        }

                        final NetworkClipboardTextMessage clipboardTextMessage = NetworkClipboardTextMessage.unmarshall(in);
                        fireOnByteReceived(1 + clipboardTextMessage.getWireSize()); // +1 : magic number (byte)
                        fireOnClipboardReceived();
                        setClipboardContents(clipboardTextMessage.getText(), clipboardOwner);
                        break;

                    case CLIPBOARD_FILES:
                        if (!introduced) {
                            throw new IOException("Unexpected message [CLIPBOARD_FILES]!");
                        }

                        final NetworkClipboardFilesMessage clipboardFiles = NetworkClipboardFilesMessage.unmarshall(in, filesHelper);
                        fireOnByteReceived(1 + clipboardFiles.getWireSize()); // +1 : magic number (byte)
                        filesHelper.setTotalFileBytesLeft(clipboardFiles.getWireSize() - 1L);

                        if (filesHelper.isIdle()) {
                            fireOnClipboardReceived();
                            filesHelper = new NetworkClipboardFilesHelper();
                            setClipboardContents(clipboardFiles.getFiles(), clipboardOwner);
                        } else {
                            filesHelper.setFiles(clipboardFiles.getFiles());
                            filesHelper.setFileNames(clipboardFiles.getFileNames());
                            filesHelper.setFileSizes(clipboardFiles.getFileSizes());
                            filesHelper.setPosition(clipboardFiles.getPosition());
                            filesHelper.setFileBytesLeft(clipboardFiles.getRemainingFileSize());
                        }
                        break;

                    case PING:
                        fireOnClipboardSent();
                        break;

                    default:
                        throw new IOException("Unsupported message type [" + type + "]!");
                }
            }
        } catch (IOException ex) {
            if (!cancelling.get()) {
                Log.error("IO error (not cancelled)", ex);
                fireOnIOError(ex);
            } else {
                Log.info("Stopped network receiver (cancelled)");
            }
            closeConnection(in, out);
        } catch (KeyManagementException | UnrecoverableKeyException e) {
            Log.error("Fatal, can not init encryption", e);
        }

        fireOnReady();
    }

    private void closeConnection(ObjectInputStream in, ObjectOutputStream out) {
        if (sender != null) {
            sender.cancel();
        }

        SystemUtilities.safeClose(in);
        SystemUtilities.safeClose(out);

        cancelling.set(false);
        server = null;
        connection = null;
        receiver = null;
    }

    private ServerSocket initServerSocket(int port) throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException, UnrecoverableKeyException, KeyManagementException {
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(NetworkAssistantHttpsEngine.class.getResourceAsStream(KEY_STORE_PATH), KEY_STORE_PASS.toCharArray());

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(keyStore, KEY_STORE_PASS.toCharArray());
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), new TrustManager[]{new CustomTrustManager()}, new SecureRandom());

        SSLServerSocketFactory ssf = sslContext.getServerSocketFactory();
        return ssf.createServerSocket(port);
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
        if (sender != null) {
            sender.sendClipboardContentFiles(files, size);
        }
    }

    private void fireOnReady() {
        final List<NetworkAssistantEngineListener> xlisteners = listeners.getListeners();

        for (final NetworkAssistantEngineListener xlistener : xlisteners) {
            xlistener.onReady();
        }
    }

    private void fireOnHttpStarting(int port) {
        final List<NetworkAssistantEngineListener> xlisteners = listeners.getListeners();

        for (final NetworkAssistantEngineListener xlistener : xlisteners) {
            xlistener.onHttpStarting(port);
        }
    }

    private void fireOnStarting(int port) {
        final List<NetworkAssistantEngineListener> xlisteners = listeners.getListeners();

        for (final NetworkAssistantEngineListener xlistener : xlisteners) {
            xlistener.onStarting(port);
        }
    }

    private void fireOnAccepting(int port) {
        final List<NetworkAssistantEngineListener> xlisteners = listeners.getListeners();

        for (final NetworkAssistantEngineListener xlistener : xlisteners) {
            xlistener.onAccepting(port);
        }
    }

    private boolean fireOnAccepted(Socket connection) {
        final List<NetworkAssistantEngineListener> xlisteners = listeners.getListeners();

        for (final NetworkAssistantEngineListener xlistener : xlisteners) {
            if (!xlistener.onAccepted(connection)) {
                return false;
            }
        }
        return true;
    }

    private void fireOnConnected(Socket connection) {
        final List<NetworkAssistantEngineListener> xlisteners = listeners.getListeners();

        for (final NetworkAssistantEngineListener xlistener : xlisteners) {
            xlistener.onConnected(connection);
        }
    }

    private void fireOnByteReceived(int count) {
        final List<NetworkAssistantEngineListener> xlisteners = listeners.getListeners();

        for (final NetworkAssistantEngineListener xlistener : xlisteners) {
            xlistener.onByteReceived(count);
        }
    }

    private void fireOnClipboardReceived() {
        final List<NetworkAssistantEngineListener> xlisteners = listeners.getListeners();

        for (final NetworkAssistantEngineListener xlistener : xlisteners) {
            xlistener.onClipboardReceived();
        }
    }

    private void fireOnClipboardSent() {
        final List<NetworkAssistantEngineListener> xlisteners = listeners.getListeners();

        for (final NetworkAssistantEngineListener xlistener : xlisteners) {
            xlistener.onClipboardSent();
        }
    }

    private void fireOnIOError(IOException error) {
        final List<NetworkAssistantEngineListener> xlisteners = listeners.getListeners();

        for (final NetworkAssistantEngineListener xlistener : xlisteners) {
            xlistener.onIOError(error);
        }
    }
}
