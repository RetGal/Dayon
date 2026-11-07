package mpo.dayon.assistant.network;

import com.dosse.upnp.UPnP;
import mpo.dayon.common.capture.CaptureEngineConfiguration;
import mpo.dayon.common.compressor.CompressorEngineConfiguration;
import mpo.dayon.common.concurrent.RunnableEx;
import mpo.dayon.common.configuration.ReConfigurable;
import mpo.dayon.common.event.Listeners;
import mpo.dayon.common.log.Log;
import mpo.dayon.common.network.NetworkEngine;
import mpo.dayon.common.network.Token;
import mpo.dayon.common.network.message.*;
import mpo.dayon.common.security.CustomTrustManager;
import mpo.dayon.common.version.Version;
import org.ice4j.ice.IceMediaStream;
import org.ice4j.ice.KeepAliveStrategy;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.time.Duration;

import static java.lang.String.format;
import static mpo.dayon.common.configuration.Configuration.DEFAULT_TOKEN_SERVER_URL;
import static mpo.dayon.common.utils.SystemUtilities.pause;
import static mpo.dayon.common.utils.SystemUtilities.safeClose;
import static mpo.dayon.common.version.Version.*;

public class NetworkAssistantEngine extends NetworkEngine implements ReConfigurable<NetworkAssistantEngineConfiguration> {

    private static final int SENDER_QUEUE_SIZE = 8;
    private static final int CONNECT_TIMEOUT_MS = 4000;
    private static final int CONNECT_GRACE_TIMEOUT_MS = 500;
    private static final long TOKEN_POLL_INTERVAL_MS = 4000L;
    private static final long REVERSE_CONNECT_RETRY_MS = 2000L;

    private final NetworkCaptureMessageHandler captureMessageHandler;
    private final NetworkMouseLocationMessageHandler mouseMessageHandler;
    private final ClipboardOwner clipboardOwner;
    private final Listeners<NetworkAssistantEngineListener> listeners = new Listeners<>();

    private NetworkAssistantEngineConfiguration configuration;
    private SSLServerSocketFactory sslServerSocketFactory;
    private IceMediaStream mediaStream;
    private SSLSocketFactory sslSocketFactory;
    private Token token;

    private boolean isReverseConnection;
    private boolean isReconnecting;
    private volatile boolean hasRejectedReverseConnection = false;

    public NetworkAssistantEngine(NetworkCaptureMessageHandler captureMessageHandler, NetworkMouseLocationMessageHandler mouseMessageHandler, ClipboardOwner clipboardOwner) {
        this.captureMessageHandler = captureMessageHandler;
        this.mouseMessageHandler = mouseMessageHandler;
        this.clipboardOwner = clipboardOwner;
        fireOnReady();
    }

    @Override
    public void configure(NetworkAssistantEngineConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public void reconfigure(NetworkAssistantEngineConfiguration configuration) {
        this.configuration = configuration;
        fireOnReconfigured(configuration);
    }

    public void addListener(NetworkAssistantEngineListener listener) {
        listeners.add(listener);
    }

    /**
     * Called from a GUI action => do not block the AWT thread (!)
     */
    public void start(boolean compatibilityMode, Token token) {
        if (cancelling.get() || receiver != null) {
            return;
        }
        this.token = token;

        receiver = new Thread(new RunnableEx() {
            @Override
            protected void doRun() throws NoSuchAlgorithmException, KeyManagementException {
                NetworkAssistantEngine.this.runSessionLoop(compatibilityMode);
            }
        }, "NetworkReceiver");
        receiver.start();
    }

    /**
     * Called from a GUI action => do not block the AWT thread (!)
     */
    public void cancel(boolean exit) {
        Log.info("Cancelling the network assistant engine...");
        if (sender != null && configuration.isTerminablePeer()) {
            sender.sendGoodbye();
            pause(100L);
        }
        // called as preExitAction - cleanup
        if (exit) {
            Log.debug("Assistant pre-exit - cleaning up UPnP port: " + configuration.getPort());
            UPnP.closePortTCP(configuration.getPort());
        }
        cancelling.set(true);
        safeClose(server, connection, fileConnection);
        fireOnDisconnecting();
    }

    private void runSessionLoop(boolean compatibilityMode) throws NoSuchAlgorithmException, KeyManagementException {
        clearPeerIdentityForReconnect();
        try {
            boolean reconnecting = false;
            do {
                reconnecting = runSession(compatibilityMode, reconnecting);
            } while (reconnecting);
        } catch (ClassNotFoundException e) {
            handleIOException(new IOException("Malformed network message", e));
        } finally {
            closeConnections();
            fireOnReady();
        }
    }

    /**
     * Runs a single session.
     *
     * @return true if the session was interrupted and another reconnect attempt should be made
     */
    private boolean runSession(boolean compatibilityMode, boolean reconnecting)
            throws NoSuchAlgorithmException, KeyManagementException, ClassNotFoundException {
        in = null;
        isReconnecting = reconnecting;
        isReverseConnection = false;

        try {
            prepareConnection(compatibilityMode);
            rememberMainPeerIdentity();
            startFileReceiver();
            initSender(SENDER_QUEUE_SIZE);
            createInputStream();

            if (!acceptReverseConnection()) {
                return false;
            }
            if (!receiveMessages(reconnecting)) {
                return false;
            }

            Log.warn("Session was interrupted - allow reconnect");
            rememberPeerIdentityForReconnect();
            fireOnSessionInterrupted();
            closeConnections(false);
            return !cancelling.get();
        } catch (IOException e) {
            handleIOException(e);
            return false;
        } catch (CertificateEncodingException e) {
            Log.error(e.getMessage());
            return false;
        }
    }

    /**
     * @return false if the reverse connection was rejected and the session must not continue
     */
    private boolean acceptReverseConnection() throws CertificateEncodingException, NoSuchAlgorithmException, IOException {
        if (!isReverseConnection) {
            return true;
        }
        if (hasRejectedReverseConnection) {
            Log.debug("Reverse connection rejected by user");
            hasRejectedReverseConnection = false;
            cancelling.set(true);
            return false;
        }
        fireOnFingerprinted(CustomTrustManager.calculateFingerprints(connection.getSession(), this.getClass().getSimpleName()));
        return true;
    }

    /**
     * @return true if the session was interrupted after the peer was introduced and a reconnect should be attempted
     */
    private boolean receiveMessages(boolean introduced) throws ClassNotFoundException, IOException {
        boolean peerIntroduced = introduced;
        try {
            while (true) {
                NetworkMessage.unmarshallMagicNumber(in); // blocking read (!)
                NetworkMessageType type = NetworkMessage.unmarshallEnum(in, NetworkMessageType.class);
                Log.debug("Received %s", type::name);

                if (peerIntroduced) {
                    if (!handleEstablishedMessage(type)) {
                        return false;
                    }
                } else {
                    peerIntroduced = handleHandshakeMessage(type);
                }
            }
        } catch (IOException e) {
            if (peerIntroduced && !cancelling.get()) {
                return true;
            }
            throw e;
        }
    }

    private void prepareConnection(boolean compatibilityMode)
            throws NoSuchAlgorithmException, IOException, KeyManagementException, CertificateEncodingException {
        fireOnStarting(configuration.getPort(), isOwnPortAccessible.get());

        if (Boolean.FALSE.equals(isOwnPortAccessible.get())) {
            if (compatibilityMode || token.getTokenString() == null) {
                Log.warn("Port not accessible from the outside, starting as server anyway");
                fireOnPeerIsAccessible(null, configuration.getPort(), false);
            } else if (tryReverseConnection()) {
                return;
            }
        }

        if (cancelling.get()) {
            throw new IOException("Cancelled");
        }
        startAsServer(compatibilityMode);
    }

    /**
     * Tries to let the assisted connect to us instead of waiting for its incoming connection.
     *
     * @return true if the reverse connection was established
     */
    private boolean tryReverseConnection() throws NoSuchAlgorithmException, IOException, KeyManagementException {
        fireOnCheckingPeerStatus(true);
        queryPeerStatus();
        fireOnCheckingPeerStatus(false);
        isReverseConnection = connectToAssistedIfPossible();
        return isReverseConnection;
    }

    private boolean connectToAssistedIfPossible() throws NoSuchAlgorithmException, IOException, KeyManagementException {
        if (!Boolean.TRUE.equals(token.isPeerAccessible())) {
            fireOnPeerIsAccessible(token.getPeerAddress(), configuration.getPort(), false);
            Log.warn("Assisted not accessible, starting as server");
            return false;
        }

        int peerPort = token.getPeerPort();
        fireOnPeerIsAccessible(token.getPeerAddress(), peerPort, true);
        Log.info("Trying to connect to the assisted");
        sslSocketFactory = CustomTrustManager.initSslContext(false).getSocketFactory();

        while (!connectToAssisted(token.getPeerAddress(), peerPort) && !cancelling.get()) {
            pause(REVERSE_CONNECT_RETRY_MS);
        }
        if (isUnexpectedReconnectPeer()) {
            throw new IOException("Reconnect aborted: peer certificate does not match the interrupted session");
        }

        hasRejectedReverseConnection = !fireOnAccepted(connection, isAutoAcceptingReconnect());
        Log.info("Connected to the assisted");
        return true;
    }

    private void queryPeerStatus() {
        String tokenServerUrl = tokenServerUrl() + token.getQueryParams();
        try {
            Log.info("Trying to obtain the assisted address");
            while (token.getPeerAddress() == null && !cancelling.get()) {
                obtainPeerAddressAndStatus(tokenServerUrl, !Boolean.TRUE.equals(isOwnPortAccessible.get()));
                if (token.isPeerAccessible() == null) {
                    pause(TOKEN_POLL_INTERVAL_MS);
                }
            }
        } catch (IOException | InterruptedException ex) {
            Log.warn("Unable to query the token server " + token.getTokenString());
            fireOnCheckingPeerStatus(false);
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static String tokenServerUrl() {
        final String customTokenServer = System.getProperty("dayon.custom.tokenServer");
        return customTokenServer == null ? DEFAULT_TOKEN_SERVER_URL : customTokenServer;
    }

    private boolean connectToAssisted(String peerAddress, int peerPort) {
        Log.debug("Assisted address is " + peerAddress);
        try {
            connection = (SSLSocket) sslSocketFactory.createSocket();
            connection.setNeedClientAuth(true);
            // grace period of 500 millis for the assisted to accept the connection
            connection.setSoTimeout(CONNECT_GRACE_TIMEOUT_MS);
            // abort the connection attempt after 5 seconds if the assisted cannot be reached
            connection.connect(new InetSocketAddress(peerAddress, peerPort), CONNECT_TIMEOUT_MS);
            // once connected, remain connected until cancelled
            connection.setSoTimeout(0);
            return true;
        } catch (IOException e) {
            Log.warn("Unable to connect to the assisted");
            return false;
        }
    }

    private boolean initFileConnection(String peerAddress, int peerPort) {
        try {
            fileConnection = (SSLSocket) sslSocketFactory.createSocket();
            // grace period of 500 millis for the assisted to accept the connection
            fileConnection.setSoTimeout(CONNECT_GRACE_TIMEOUT_MS);
            // abort the connection attempt after 5 seconds if the assistant cannot be reached
            fileConnection.connect(new InetSocketAddress(peerAddress, peerPort), CONNECT_TIMEOUT_MS);
            // once connected, remain connected until cancelled
            fileConnection.setSoTimeout(0);
            return true;
        } catch (IOException e) {
            Log.warn("Unable to connect to the assisted (file server)");
            return false;
        }
    }

    private void obtainPeerAddressAndStatus(String tokenServerUrl, boolean closed) throws IOException, InterruptedException {
        String query = format(tokenServerUrl, token.getTokenString(), closed ? 1 : 0, nullToZeroStr(getLocalAddress()));
        Log.debug("Querying token server " + query);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(query))
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofSeconds(4L))
                .build();
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        Log.debug("Got %s", () -> response.body().trim());
        String[] parts = response.body().trim().split("\\*");
        Log.debug("Length " + parts.length);
        if (!isProcessableResponse(parts)) {
            return;
        }
        String iceInfo = parts.length > 5 ? parts[5] : null;
        // 0 assisted addr, 1 assisted port, 2 assisted local addr, 3 assisted open (1 = open), 4 our local port, 5 assisted ice
        token.updateToken(parts[0], Integer.parseInt(parts[1]), parts[2], !"0".equals(parts[3]), Integer.parseInt(parts[4]), iceInfo);
    }

    // ignore incomplete responses and those with unknown open status "-1"
    private static boolean isProcessableResponse(String[] parts) {
        return parts.length > 4
                && !parts[1].isEmpty()
                && !parts[4].isEmpty()
                && !parts[3].isEmpty()
                && !parts[3].equals("-1");
    }

    private void startFileReceiver() {
        fileReceiver = new Thread(new RunnableEx() {
            @Override
            protected void doRun() {
                receiveClipboardFiles();
            }
        }, "FileReceiver");
        fileReceiver.start();
    }

    private void receiveClipboardFiles() {
        fileIn = null;
        Log.info(format("Dayon! file server [port:%d]", configuration.getPort()));
        try {
            if (!establishFileConnection()) {
                return;
            }
            if (!isFileConnectionBound(fileConnection)) {
                Log.warn("Rejecting file connection: it is not bound to the main session");
                safeClose(fileConnection);
                fileConnection = null;
                return;
            }
            initFileSender();
            fileIn = new ObjectInputStream(new BufferedInputStream(fileConnection.getInputStream()));
            NetworkClipboardFilesMessage.installFileObjectInputFilter(fileIn);
            handleIncomingClipboardFiles(fileIn, clipboardOwner);
        } catch (IOException ex) {
            handleFileReceiverError();
        }
    }

    /**
     * @return true if the file connection has been established
     */
    private boolean establishFileConnection() throws IOException {
        if (isReverseConnection) {
            if (initFileConnection(token.getPeerAddress(), token.getPeerPort())) {
                return true;
            }
            Log.warn("Unable to establish the file connection");
            return false;
        }

        server = (SSLServerSocket) sslServerSocketFactory.createServerSocket(configuration.getPort());
        server.setNeedClientAuth(true);
        fileConnection = (SSLSocket) server.accept();
        safeClose(server);
        server = null;
        return true;
    }

    private void handleFileReceiverError() {
        if (fileReceiver == Thread.currentThread()) {
            closeConnections();
        } else {
            Log.debug("Ignoring I/O error of a stale file receiver");
        }
    }

    private boolean handleEstablishedMessage(NetworkMessageType type) throws IOException, ClassNotFoundException {
        try {
            switch (type) {
                case CAPTURE:
                    final NetworkCaptureMessage capture = NetworkCaptureMessage.unmarshall(in);
                    fireOnMessageReceived(capture);
                    captureMessageHandler.handleCapture(capture);
                    return true;
                case MOUSE_LOCATION:
                    final NetworkMouseLocationMessage mouse = NetworkMouseLocationMessage.unmarshall(in);
                    fireOnMessageReceived(mouse);
                    mouseMessageHandler.handleLocation(mouse);
                    return true;
                case CLIPBOARD_TEXT:
                    final NetworkClipboardTextMessage clipboardText = NetworkClipboardTextMessage.unmarshall(in);
                    fireOnMessageReceived(clipboardText);
                    setClipboardContents(clipboardText.getText(), clipboardOwner);
                    fireOnClipboardReceived();
                    return true;
                case CLIPBOARD_GRAPHIC:
                    final NetworkClipboardGraphicMessage clipboardGraphic = NetworkClipboardGraphicMessage.unmarshall(in);
                    fireOnMessageReceived(clipboardGraphic);
                    setClipboardContents(clipboardGraphic.getGraphic().getTransferData(DataFlavor.imageFlavor), clipboardOwner);
                    fireOnClipboardReceived();
                    return true;
                case PING:
                    fireOnClipboardSent();
                    return true;
                case RESIZE:
                    final NetworkResizeScreenMessage resize = NetworkResizeScreenMessage.unmarshall(in);
                    fireOnMessageReceived(resize);
                    fireOnResizeScreen(resize.getWidth(), resize.getHeight());
                    return true;
                case GOODBYE:
                    fireOnTerminating();
                    return false;
                case HELLO:
                    fireOnConnected(connection, receiveHello());
                    return true;
                default:
                    throw new IOException(format(UNSUPPORTED_TYPE, type));
            }
        } catch (RuntimeException ex) {
            // malformed input must end this session, not the process
            throw new IOException("Malformed network message", ex);
        }
    }

    private boolean handleHandshakeMessage(NetworkMessageType type) throws IOException {
        try {
            switch (type) {
                case HELLO:
                    fireOnConnected(connection, receiveHello());
                    return true;
                case PING:
                case CAPTURE:
                case MOUSE_LOCATION:
                case CLIPBOARD_TEXT:
                case CLIPBOARD_GRAPHIC:
                case CLIPBOARD_FILES:
                case GOODBYE:
                    return false;
                default:
                    throw new IOException(format(UNSUPPORTED_TYPE, type));
            }
        } catch (RuntimeException ex) {
            // malformed input must end this session, not the process
            throw new IOException("Malformed network message", ex);
        }
    }

    private NetworkHelloMessage receiveHello() throws IOException {
        final NetworkHelloMessage hello = NetworkHelloMessage.unmarshall(in);
        fireOnMessageReceived(hello);
        if (!isCompatibleVersion(hello.getMajor(), hello.getMinor(), Version.get())) {
            Log.error(format("Incompatible assisted version: %d.%d", hello.getMajor(), hello.getMinor()));
            throw new IOException("version.wrong");
        }
        configuration.setMonochromePeer(!isColoredVersion(hello.getMajor()));
        configuration.setTerminablePeer(isTerminable(hello.getMajor()));
        return hello;
    }

    private void fireOnMessageReceived(NetworkMessage message) {
        fireOnByteReceived(1 + message.getWireSize()); // +1 : magic number (byte)
    }

    /**
     * Might be blocking if the sender queue is full (!)
     */
    public void sendCaptureConfiguration(CaptureEngineConfiguration configuration) {
        if (sender != null) {
            sender.sendCaptureConfiguration(configuration, this.configuration.isMonochromePeer());
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
    public void sendScreenshotRequest() {
        if (sender != null) {
            sender.sendScreenshotRequest();
        }
    }

    private void startAsServer(boolean compatibilityMode)
            throws NoSuchAlgorithmException, IOException, KeyManagementException, CertificateEncodingException {
        sslServerSocketFactory = CustomTrustManager.initSslContext(compatibilityMode).getServerSocketFactory();
        Log.info(format("Dayon! server [port:%d]", configuration.getPort()));
        if (compatibilityMode) {
            Log.warn("Compatibility mode enabled, using legacy certificate");
        }

        closeServerIfBound();
        server = (SSLServerSocket) sslServerSocketFactory.createServerSocket(configuration.getPort());
        server.setNeedClientAuth(true);

        acceptAssistedConnection();
        if (connection == null) {
            throw new IOException("Reconnect aborted: no matching peer");
        }
        fireOnFingerprinted(CustomTrustManager.calculateFingerprints(connection.getSession(), this.getClass().getSimpleName()));

        closeServerIfBound();
        server = null;
    }

    private void closeServerIfBound() {
        if (server != null && server.isBound()) {
            safeClose(server);
        }
    }

    private void acceptAssistedConnection() throws IOException {
        Log.info("Waiting for the assisted to connect...");
        boolean accepted;
        do {
            if (connection != null && connection.isBound()) {
                safeClose(connection); // we might have refused the accepted connection (!)
            }
            connection = (SSLSocket) server.accept();
            fireOnConnecting();
            if (!connection.getSession().isValid()) {
                fireOnFingerprinted(null);
                throw new IOException("Certificate error, try enabling compatibility mode!");
            }
            if (isUnexpectedReconnectPeer()) {
                Log.warn(format("Rejecting reconnect from %s: peer certificate does not match the interrupted session",
                        connection.getInetAddress().getHostAddress()));
                safeClose(connection);
                connection = null;
                accepted = false;
            } else {
                Log.info(format("Incoming connection from %s", connection.getInetAddress().getHostAddress()));
                accepted = fireOnAccepted(connection, isAutoAcceptingReconnect());
            }
        } while (!accepted && !cancelling.get());
    }

    private boolean isUnexpectedReconnectPeer() {
        return isReconnecting && hasPeerIdentityForReconnect() && !isExpectedReconnectPeer(connection);
    }

    private boolean isAutoAcceptingReconnect() {
        return configuration.isAutoAccept() || (isReconnecting && hasPeerIdentityForReconnect());
    }

    public void initIceAgent() throws IOException {
        super.initializeIceAgent();
        createMediaStream();
    }

    private void createMediaStream() throws IOException {
        if (mediaStream != null) {
            iceAgent.removeStream(mediaStream);
        }
        int port = configuration.getPort();
        mediaStream = iceAgent.createMediaStream("dayon");
        iceAgent.createComponent(mediaStream, port, port, port, KeepAliveStrategy.SELECTED_AND_TCP);
    }

    private void fireOnReady() {
        listeners.getListeners().forEach(NetworkAssistantEngineListener::onReady);
    }

    private void fireOnStarting(int port, boolean isOwnPortAccessible) {
        listeners.getListeners().forEach(listener -> listener.onStarting(port, isOwnPortAccessible));
    }

    private void fireOnPeerIsAccessible(String address, int port, boolean isPeerAccessible) {
        listeners.getListeners().forEach(listener -> listener.onPeerIsAccessible(address, port, isPeerAccessible));
    }

    private void fireOnCheckingPeerStatus(boolean blink) {
        listeners.getListeners().forEach(listener -> listener.onCheckingPeerStatus(blink));
    }

    private void fireOnConnecting() {
        listeners.getListeners().forEach(NetworkAssistantEngineListener::onConnecting);
    }

    private boolean fireOnAccepted(Socket connection, boolean autoAccept) {
        return listeners.getListeners().stream().allMatch(listener -> listener.onAccepted(connection, autoAccept));
    }

    private void fireOnConnected(Socket connection, NetworkHelloMessage hello) {
        listeners.getListeners().forEach(listener -> listener.onConnected(connection, hello.getOsId(), hello.getInputLocale(), hello.getMajor()));
    }

    private void fireOnByteReceived(int count) {
        listeners.getListeners().forEach(listener -> listener.onByteReceived(count));
    }

    @Override
    protected void fireOnClipboardReceived() {
        listeners.getListeners().forEach(NetworkAssistantEngineListener::onClipboardReceived);
    }

    private void fireOnClipboardSent() {
        listeners.getListeners().forEach(NetworkAssistantEngineListener::onClipboardSent);
    }

    private void fireOnResizeScreen(int width, int height) {
        listeners.getListeners().forEach(listener -> listener.onResizeScreen(width, height));
    }

    private void fireOnDisconnecting() {
        listeners.getListeners().forEach(NetworkAssistantEngineListener::onDisconnecting);
    }

    private void fireOnTerminating() {
        listeners.getListeners().forEach(NetworkAssistantEngineListener::onTerminating);
    }

    @Override
    protected void fireOnIOError(IOException error) {
        listeners.getListeners().forEach(listener -> listener.onIOError(error));
    }

    private void fireOnFingerprinted(String fingerprints) {
        listeners.getListeners().forEach(listener -> listener.onFingerprinted(fingerprints));
    }

    private void fireOnReconfigured(NetworkAssistantEngineConfiguration configuration) {
        listeners.getListeners().forEach(listener -> listener.onReconfigured(configuration));
    }

    private void fireOnSessionInterrupted() {
        listeners.getListeners().forEach(NetworkAssistantEngineListener::onSessionInterrupted);
    }

}
