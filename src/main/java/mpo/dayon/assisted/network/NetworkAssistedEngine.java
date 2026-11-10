package mpo.dayon.assisted.network;

import com.dosse.upnp.UPnP;
import mpo.dayon.assisted.control.NetworkControlMessageHandler;
import mpo.dayon.assisted.mouse.MouseEngineListener;
import mpo.dayon.assisted.utils.ScreenUtilities;
import mpo.dayon.common.buffer.MemByteBuffer;
import mpo.dayon.common.compressor.CompressorEngineConfiguration;
import mpo.dayon.common.compressor.CompressorEngineListener;
import mpo.dayon.common.concurrent.RunnableEx;
import mpo.dayon.common.configuration.ReConfigurable;
import mpo.dayon.common.error.FatalErrorHandler;
import mpo.dayon.common.event.Listeners;
import mpo.dayon.common.log.Log;
import mpo.dayon.common.network.NetworkEngine;
import mpo.dayon.common.network.SdpUtils;
import mpo.dayon.common.network.Token;
import mpo.dayon.common.network.message.*;
import mpo.dayon.common.security.CustomTrustManager;
import mpo.dayon.common.squeeze.CompressionMethod;
import org.ice4j.TransportAddress;
import org.ice4j.ice.*;
import org.ice4j.ice.Component;

import javax.net.ssl.*;
import javax.sdp.SdpException;
import java.awt.*;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.beans.PropertyChangeEvent;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateEncodingException;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.String.format;
import static mpo.dayon.common.configuration.Configuration.DEFAULT_TOKEN_SERVER_URL;
import static mpo.dayon.common.utils.SystemUtilities.*;

public class NetworkAssistedEngine extends NetworkEngine
        implements ReConfigurable<NetworkAssistedEngineConfiguration>, CompressorEngineListener, MouseEngineListener {

    private static final int SESSION_QUEUE_SIZE = 1;
    private static final int RECEIVER_STARTUP_DELAY_MS = 100;
    private static final long RECONNECT_DELAY_MS = 1500L;
    private static final long ICE_TIMEOUT_MS = 6000L;
    private static final int ICE_CONNECT_TIMEOUT_MS = 3000;
    private static final int ICE_CONNECT_DELAY_MS = 250;
    private static final int RANDOM_PORT_START = 1026;
    private static final int RANDOM_PORT_RANGE = 8976;

    private NetworkAssistedEngineConfiguration configuration;
    private final NetworkCaptureConfigurationMessageHandler captureConfigurationHandler;
    private final NetworkCompressorConfigurationMessageHandler compressorConfigurationHandler;
    private final NetworkControlMessageHandler controlHandler;
    private final NetworkClipboardRequestMessageHandler clipboardRequestHandler;
    private final NetworkScreenshotRequestMessageHandler screenshotRequestHandler;
    private final ClipboardOwner clipboardOwner;
    private final Listeners<NetworkAssistedEngineListener> listeners = new Listeners<>();
    private final char osId = System.getProperty("os.name").toLowerCase().charAt(0);
    private final SecureRandom random = new SecureRandom();
    /**
     * One-way stop flag set by {@link #cancel()}. Unlike {@code cancelling}, it is never
     * reset by connection cleanup, so a reconnect will not be started after a cancel (i.e. stop button).
     */
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);

    private SSLSocketFactory sslSocketFactory;
    private Token token;
    private IceMediaStream mediaStream;

    private String publicIp;
    private int connectionTimeout;
    private boolean iceConnected;
    private boolean isRevertedConnection;

    public NetworkAssistedEngine(NetworkCaptureConfigurationMessageHandler captureConfigurationHandler,
                                 NetworkCompressorConfigurationMessageHandler compressorConfigurationHandler,
                                 NetworkControlMessageHandler controlHandler,
                                 NetworkClipboardRequestMessageHandler clipboardRequestHandler,
                                 NetworkScreenshotRequestMessageHandler screenshotRequestHandler, ClipboardOwner clipboardOwner) {
        this.captureConfigurationHandler = captureConfigurationHandler;
        this.compressorConfigurationHandler = compressorConfigurationHandler;
        this.controlHandler = controlHandler;
        this.clipboardRequestHandler = clipboardRequestHandler;
        this.screenshotRequestHandler = screenshotRequestHandler;
        this.clipboardOwner = clipboardOwner;
    }

    public NetworkAssistedEngineConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public void configure(NetworkAssistedEngineConfiguration configuration) {
        Log.debug(format("New configuration %s", configuration));
        this.configuration = configuration;
    }

    @Override
    public void reconfigure(NetworkAssistedEngineConfiguration configuration) {
        this.configuration = configuration;
        fireOnReconfigured(configuration);
    }

    public void addListener(NetworkAssistedEngineListener listener) {
        listeners.add(listener);
    }

    /*
     * Connect to the assistant using the given token, which may be blank. The connectionTimeout is in ms
     */
    public void connect(Token token, int connectionTimeout) {
        if (isCancelled()) {
            Log.info("Connection attempt cancelled");
            return;
        }
        connection = null;
        this.token = token;
        this.connectionTimeout = connectionTimeout;
        try {
            startSession();
        } catch (UnknownHostException e) {
            if (abortIfCancelled()) {
                return;
            }
            fireOnHostNotFound(configuration);
        } catch (SocketTimeoutException e) {
            if (abortIfCancelled()) {
                return;
            }
            fireOnConnectionTimeout(configuration);
        } catch (IOException e) {
            if (abortIfCancelled()) {
                return;
            }
            closeConnections();
            fireOnRefused(configuration);
        } catch (NoSuchAlgorithmException | KeyManagementException | CertificateEncodingException e) {
            FatalErrorHandler.bye(e.getMessage(), e);
        }
    }

    /**
     * @return true when the user cancelled or the peer ended the session
     */
    private boolean isCancelled() {
        return stopRequested.get() || cancelling.get();
    }

    /**
     * Stops the connection attempt when the user has cancelled it.
     *
     * @return true when the attempt was cancelled
     */
    private boolean abortIfCancelled() {
        if (!isCancelled()) {
            return false;
        }
        Log.info("Connection attempt cancelled");
        closeConnections();
        return true;
    }

    private void startSession() throws IOException, NoSuchAlgorithmException, KeyManagementException, CertificateEncodingException {
        Log.debug(token.toString());
        sslSocketFactory = CustomTrustManager.initSslContext(false).getSocketFactory();
        isRevertedConnection = false;

        if (!resolveIncompleteToken()) {
            return;
        }
        fireOnConnecting(configuration);
        if (abortIfCancelled()) {
            return;
        }

        boolean assistantInSameNetwork = false;
        if (Boolean.FALSE.equals(token.isPeerAccessible())) {
            assistantInSameNetwork = prepareFallbackConnection();
        }
        if (abortIfCancelled()) {
            return;
        }

        if (connection == null && isDirectConnectionPossible(assistantInSameNetwork)) {
            fireOnPeerIsAccessible(true);
            if (Boolean.TRUE.equals(token.isPeerAccessible())) {
                applyTokenToConfiguration();
            }
            connectToAssistant(connectionTimeout, 0);
        }

        if (connection == null && token.getIceInfo() != null) {
            connectViaIce();
        }
        if (abortIfCancelled()) {
            return;
        }

        completeConnection();
    }

    /**
     * @return false if the token could not be resolved and the connection attempt must be aborted
     */
    private boolean resolveIncompleteToken() throws IOException {
        if (token.getTokenString() == null || token.getPeerAddress() != null) {
            return true;
        }

        Log.debug("Incomplete Token, resolving " + token);
        checkAndUpdateRVS(0, true);
        if (token.getPeerAddress() == null || token.getPeerPort() == 0) {
            Log.warn("Token resolution failed");
            return false;
        }

        applyTokenToConfiguration();
        configuration.persist();
        return true;
    }

    /**
     * Handles the case where the assistant cannot be reached directly.
     *
     * @return true if the assistant was found in the same local network
     */
    private boolean prepareFallbackConnection() throws IOException, NoSuchAlgorithmException, KeyManagementException {
        fireOnPeerIsAccessible(false);
        Log.info("Assistant is not accessible directly");

        int localPort = detectEnvironment();
        checkAndUpdateRVS(localPort, false);
        Log.debug(String.valueOf(token));
        applyTokenToConfiguration();

        if (Boolean.TRUE.equals(isOwnPortAccessible.get())) {
            Log.info("Reverting the connection initialization");
            fireOnAccepting(localPort);
            startServer(localPort);
            Log.debug("Connected");
            isRevertedConnection = true;
            return false;
        }

        if (token.getIceInfo() == null) {
            // last resort for legacy, non-ICE, non-token connections
            return detectLocalAssistant();
        }
        return false;
    }

    private boolean isDirectConnectionPossible(boolean assistantInSameNetwork) {
        return token.getTokenString() == null
                || Boolean.TRUE.equals(token.isPeerAccessible())
                || assistantInSameNetwork;
    }

    private void applyTokenToConfiguration() {
        Log.debug("Updating configuration ServerName and ServerPort with Token values");
        configuration.setServerName(token.getPeerAddress());
        configuration.setServerPort(token.getPeerPort());
    }

    private void completeConnection() throws IOException, NoSuchAlgorithmException, CertificateEncodingException {
        createInputStream();
        startReceiversIfNeeded();
        receiver.start();
        pause(RECEIVER_STARTUP_DELAY_MS);
        initSender(SESSION_QUEUE_SIZE);
        sender.sendHello(osId);
        rememberMainPeerIdentity();

        // only if we initiated the connection, we also need to establish a file connection
        if (!isRevertedConnection) {
            fileConnection = (SSLSocket) sslSocketFactory.createSocket(configuration.getServerName(), configuration.getServerPort());
            disableNagle(fileConnection);
            Log.debug("File connection established");
        }
        if (!isFileConnectionBound(fileConnection)) {
            throw new IOException("File connection is not bound to the main session");
        }
        if (abortIfCancelled()) {
            return;
        }

        fireOnConnected(CustomTrustManager.calculateFingerprints(connection.getSession(), this.getClass().getSimpleName()), isRevertedConnection);
        Log.info("Connected with the assistant!");
        initFileSender();
        createFileInputStream();
        fileReceiver.start();
    }

    private void startReceiversIfNeeded() {
        if (receiver != null) {
            return;
        }
        Log.info("Getting the receivers ready");
        receiver = new Thread(new RunnableEx() {
            @Override
            protected void doRun() {
                NetworkAssistedEngine.this.receivingLoop();
            }
        }, "CommandReceiver");

        fileReceiver = new Thread(new RunnableEx() {
            @Override
            protected void doRun() {
                NetworkAssistedEngine.this.fileReceivingLoop();
            }
        }, "FileReceiver");
    }

    private void connectViaIce() throws IOException {
        if (isCancelled()) {
            freeIceAgent();
            return;
        }
        fireOnIceConnecting();
        try {
            Log.info("Starting ICE");
            connectIce(token.getLocalPort());
        } catch (IOException e) {
            if (!isCancelled()) {
                // try again with another port - the old agent must not keep harvesting or checking
                freeIceAgent();
                token.setLocalPort(detectEnvironment());
                Log.info("Restarting ICE");
                connectIce(token.getLocalPort());
            }
        } finally {
            if (isCancelled()) {
                Log.info("ICE connection cancelled");
                freeIceAgent();
            }
        }
    }

    private void connectIce(int localPort) throws IOException {
        initIceAgent(localPort);
        // force candidate harvesting before the remote description is applied
        getLocalSdpDesc();

        // wait for ICE connection using an event-driven latch with a bounded timeout
        final CountDownLatch latch = new CountDownLatch(1);
        monitorIceProcessingState(latch);

        // process remote SDP and start connectivity establishment
        processRemoteSdp(new String(Base64.getDecoder().decode(token.getIceInfo())));

        final long start = System.nanoTime();
        final long deadline = start + TimeUnit.MILLISECONDS.toNanos(ICE_TIMEOUT_MS);
        try {
            boolean signaled = false;
            while (!signaled && !isCancelled() && System.nanoTime() < deadline) {
                signaled = latch.await(100L, TimeUnit.MILLISECONDS);
            }
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            Log.debug("ICE wait elapsed: " + elapsed + "ms, signaled=" + signaled + ", iceConnected=" + iceConnected);
            if (isCancelled()) {
                throw new IOException("Cancelled");
            }
            if (connection == null || !iceConnected) {
                Log.info("ICE connection failed");
                cleanUpUpnp(localPort);
                throw new IOException("ICE connection failed");
            }
            Log.info("ICE connection succeeded");
            Log.debug("ICE connection: " + connection);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.warn("ICE wait interrupted");
            throw new IOException("ICE wait interrupted", e);
        }
    }

    /**
     * Stops the ICE agent so that it does not keep harvesting candidates or running connectivity checks.
     */
    private synchronized void freeIceAgent() {
        if (iceAgent != null) {
            Log.info("Stopping ICE agent");
            iceAgent.free();
            iceAgent = null;
            mediaStream = null;
            iceConnected = false;
        }
    }

    private void monitorIceProcessingState(CountDownLatch latch) {
        iceAgent.addStateChangeListener(evt -> {
            IceProcessingState newState = (IceProcessingState) evt.getNewValue();
            Log.debug("ICE state changed: " + newState);

            if (newState == IceProcessingState.COMPLETED) {
                handleIceCompleted(evt);
            } else if (newState == IceProcessingState.FAILED) {
                iceConnected = false;
                Log.error("ICE processing failed");
            }

            if (newState == IceProcessingState.FAILED || newState == IceProcessingState.COMPLETED || newState == IceProcessingState.TERMINATED) {
                latch.countDown();
            }
        });
    }

    private void handleIceCompleted(PropertyChangeEvent evt) {
        if (isCancelled()) {
            iceConnected = false;
            Log.info("ICE processing cancelled");
            return;
        }
        Log.info("ICE processing completed");
        Agent agent = (Agent) evt.getSource();
        Log.debug("ICE stream names: " + agent.getStreamNames());
        IceMediaStream stream = agent.getStream("dayon");
        if (stream == null) {
            iceConnected = false;
            Log.error("ICE processing failed: dayon stream not found");
            return;
        }

        Component rtpComponent = stream.getComponent(Component.RTP);
        CandidatePair rtpPair = rtpComponent.getSelectedPair();
        if (rtpPair == null) {
            iceConnected = false;
            Log.error("ICE processing failed: rtpPair is null");
            return;
        }

        TransportAddress address = rtpPair.getRemoteCandidate().getTransportAddress();
        configuration.setServerName(address.getHostName());
        configuration.setServerPort(address.getPort());
        fireOnConnecting(configuration);
        try {
            connectToAssistant(ICE_CONNECT_TIMEOUT_MS, ICE_CONNECT_DELAY_MS);
            if (isCancelled()) {
                Log.info("ICE processing cancelled");
                safeClose(connection);
                connection = null;
                iceConnected = false;
                return;
            }
            iceConnected = true;
        } catch (IOException e) {
            iceConnected = false;
            Log.error(format("ICE connection to %s:%s failed", configuration.getServerName(), configuration.getServerPort()));
        }
    }

    private void processRemoteSdp(String remoteSdp) {
        try {
            SdpUtils.parseSDP(iceAgent, remoteSdp);
            iceAgent.startConnectivityEstablishment();
        } catch (SdpException e) {
            Log.error("Failed to process remote SDP", e);
        }
    }

    private void initIceAgent(int port) throws IOException {
        super.initializeIceAgent(true);
        createMediaStream(port);
    }

    private void createMediaStream(int port) throws IOException {
        if (mediaStream != null) {
            iceAgent.removeStream(mediaStream);
        }
        mediaStream = iceAgent.createMediaStream("dayon");
        iceAgent.createComponent(mediaStream, port, port - 1, port + 1, KeepAliveStrategy.SELECTED_AND_TCP);
    }

    private boolean detectLocalAssistant() {
        if (publicIp.equals(token.getPeerAddress())) {
            Log.info("Assistant is in the same network");
            configuration.setServerName(token.getPeerLocalAddress());
            configuration.setServerPort(token.getPeerPort());
            fireOnConnecting(configuration);
            // grace period for the assistant to get ready
            pause(4000L);
            return true;
        }
        // guess we are out of options
        Log.debug("Out of options");
        fireOnRefused(configuration);
        return false;
    }

    private void startServer(int port) throws NoSuchAlgorithmException, KeyManagementException {
        try {
            SSLServerSocketFactory serverSocketFactory = CustomTrustManager.initSslContext(false).getServerSocketFactory();
            Log.info(format("Dayon! server [port:%d]", port));
            server = (SSLServerSocket) serverSocketFactory.createServerSocket(port);
            server.setNeedClientAuth(true);

            Log.info("Waiting for the assistant to connect...");
            connection = (SSLSocket) server.accept();
            disableNagle(connection);
            Toolkit.getDefaultToolkit().beep();
            Log.info(format("Incoming connection from %s", connection.getInetAddress().getHostAddress()));
        } catch (IOException e) {
            Log.error("Error accepting incoming connection", e);
            closeConnections();
            return;
        }

        try {
            fileConnection = (SSLSocket) server.accept();
            disableNagle(fileConnection);
            safeClose(server);
            Log.debug("File connection established");
        } catch (IOException e) {
            Log.error("Error establishing file connection", e);
            closeConnections();
        }
    }

    private void checkAndUpdateRVS(int localPort, boolean incomplete) throws IOException {
        try {
            String response = queryTokenServer(localPort, incomplete);
            String[] parts = response.split("\\*");
            if (parts.length <= 1) {
                return;
            }

            Log.debug("Length: " + parts.length);
            String iceInfo = parts.length > 5 ? parts[5] : null;
            Log.debug(iceInfo == null ? "Received no ICE information" : "Received ICE information");
            // part 3 (closed) is 0 or a timestamp -> 0 means the assistant is accessible
            token.updateToken(parts[0], Integer.parseInt(parts[1]), parts[2], parts[3].equals("0"), localPort, iceInfo);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Queries the token server for the assistant address, optionally including ICE information.
     * <p>
     * Response format:
     * <pre>
     * 188.155.141.250*8090*192.168.1.40*1786374911*0*dj0wDQpvPWljZTRqLm9yZyAwIDAgSU4gSVA0IDE4OC4xNTU
     * |               |        |          |        |        iceInfo (optional)
     * |               |        |          |        localPort
     * |               |        |          timestamp / closed status (0 = accessible)
     * |               |        peer local address
     * |               peer port
     * peer address
     * </pre>
     */
    private String queryTokenServer(int localPort, boolean incomplete) throws IOException, InterruptedException {
        String queryParams = incomplete ? token.getQueryParams() + "&inc" : token.getQueryParams();
        final String customTokenServer = System.getProperty("dayon.custom.tokenServer");
        String tokenServerUrl = customTokenServer == null ? DEFAULT_TOKEN_SERVER_URL : customTokenServer;
        return resolveToken(tokenServerUrl + queryParams, token.getTokenString(), localPort, isOwnPortAccessible.get(), getLocalAddress());
    }

    // returns a random port number for which an upnp port mapping was added (if possible)
    private int detectEnvironment() {
        if (publicIp == null) {
            publicIp = resolvePublicIp();
        }
        int newPort = random.nextInt(RANDOM_PORT_RANGE) + RANDOM_PORT_START;
        selfTest(publicIp, newPort, token.getLocalPort());
        return newPort;
    }

    @SuppressWarnings("java:S2095") // our sockets MUST NOT be closed
    private void connectToAssistant(int connectionTimeout, int preDelay) throws IOException {
        try {
            if (preDelay > 0) {
                pause(preDelay);
            }
            Log.info(format("Connecting to [%s:%s]", configuration.getServerName(), configuration.getServerPort()));
            connection = (SSLSocket) sslSocketFactory.createSocket();
            connection.setNeedClientAuth(true);
            // grace period of twice the connection timeout (default 14 seconds) for the assistant to accept the connection
            connection.setSoTimeout(2 * connectionTimeout);
            // abort the connection attempt after connection timeout (default 7 seconds) if the assistant cannot be reached
            connection.connect(new InetSocketAddress(configuration.getServerName(), configuration.getServerPort()), connectionTimeout);
            // once connected, remain connected until cancelled
            connection.setSoTimeout(0);
            disableNagle(connection);
        } catch (UnknownHostException e) {
            Log.warn("Unable to connect to the assistant - unknown host");
            throw e;
        } catch (SocketTimeoutException e) {
            Log.warn("Unable to connect to the assistant - connection timeout");
            throw e;
        } catch (IOException e) {
            Log.warn("Unable to connect to the assistant");
            throw e;
        }
    }

    private void createFileInputStream() throws IOException {
        fileIn = new ObjectInputStream(new BufferedInputStream(fileConnection.getInputStream()));
        NetworkClipboardFilesMessage.installFileObjectInputFilter(fileIn);
    }

    public static String resolveToken(String tokenServerUrl, String token, int port, Boolean open, String localAddress) throws IOException, InterruptedException {
        if (open == null) {
            isOwnPortAccessible.set(null);
        }
        // null = unknown => -1, true = open => 1, false = closed => 0
        String query = format(tokenServerUrl, token, port, toInt(open), nullToZeroStr(localAddress));
        Log.debug("Resolving token using: " + query);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(query))
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofSeconds(5))
                .build();
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Failed to obtain token, status code: " + response.statusCode());
        }
        final String responseString = response.body().trim();
        Log.debug("Token resolved: " + responseString);
        return responseString;
    }

    private static int toInt(Boolean open) {
        if (open == null) {
            return -1;
        }
        return open ? 1 : 0;
    }

    /**
     * Called from a GUI action => do not block the AWT thread (!)
     */
    public void cancel() {
        Log.info("Cancelling the network assisted engine...");
        stopRequested.set(true);
        closeConnections();
        fireOnDisconnecting();
        cleanUpUpnp();
    }

    private void cleanUpUpnp() {
        if (token != null && token.getLocalPort() != 0) {
            cleanUpUpnp(token.getLocalPort());
        }
    }

    private void cleanUpUpnp(int localPort) {
        if (localPort != 0) {
            Log.debug("Cleaning up UPnP port: " + localPort);
            CompletableFuture.runAsync(() -> UPnP.closePortTCP(localPort));
        }
    }

    @SuppressWarnings("squid:S2189")
    private void receivingLoop() {
        try {
            while (true) {
                processNetworkMessages();
            }
        } catch (IOException ex) {
            final boolean cancelled = isCancelled();
            closeConnections();
            if (cancelled) {
                Log.info("Stopped network receiver (cancelled)");
                fireOnDisconnecting();
                cleanUpUpnpUnlessReverted();
                return;
            }
            if (isRevertedConnection) {
                return;
            }
            cleanUpUpnp();
            pause(RECONNECT_DELAY_MS);
            if (isCancelled()) {
                Log.info("Reconnect cancelled");
                return;
            }
            Log.warn("Session was interrupted - reconnect");
            connect(token, connectionTimeout);
        }
    }

    private void cleanUpUpnpUnlessReverted() {
        if (!isRevertedConnection) {
            cleanUpUpnp();
        }
    }

    private void processNetworkMessages() throws IOException {
        try {
            NetworkMessage.unmarshallMagicNumber(in); // blocking read (!)
            NetworkMessageType type = NetworkMessage.unmarshallEnum(in, NetworkMessageType.class);
            Log.debug("Received %s", type::name);

            switch (type) {
                case CAPTURE_CONFIGURATION:
                    captureConfigurationHandler.handleConfiguration(NetworkCaptureConfigurationMessage.unmarshall(in));
                    break;
                case COMPRESSOR_CONFIGURATION:
                    compressorConfigurationHandler.handleConfiguration(NetworkCompressorConfigurationMessage.unmarshall(in));
                    break;
                case MOUSE_CONTROL:
                    controlHandler.handleMessage(NetworkMouseControlMessage.unmarshall(in));
                    break;
                case KEY_CONTROL:
                    controlHandler.handleMessage(NetworkKeyControlMessage.unmarshall(in));
                    break;
                case CLIPBOARD_REQUEST:
                    clipboardRequestHandler.handleClipboardRequest();
                    break;
                case CLIPBOARD_TEXT:
                    handleClipboardText(NetworkClipboardTextMessage.unmarshall(in));
                    break;
                case CLIPBOARD_GRAPHIC:
                    handleClipboardGraphic(NetworkClipboardGraphicMessage.unmarshall(in));
                    break;
                case SCREENSHOT_REQUEST:
                    screenshotRequestHandler.handleScreenshotRequest();
                    break;
                case GOODBYE:
                    cancelling.set(true);
                    break;
                case PING:
                    break;
                default:
                    throw new IOException(format(UNSUPPORTED_TYPE, type));
            }
        } catch (RuntimeException | ClassNotFoundException ex) {
            // malformed input must end this session, not the process
            throw new IOException("Malformed network message", ex);
        }
    }

    private void handleClipboardText(NetworkClipboardTextMessage message) {
        setClipboardContents(message.getText(), clipboardOwner);
        sender.ping();
    }

    private void handleClipboardGraphic(NetworkClipboardGraphicMessage message) {
        setClipboardContents(message.getGraphic().getTransferData(DataFlavor.imageFlavor), clipboardOwner);
        sender.ping();
    }

    private void fileReceivingLoop() {
        try {
            handleIncomingClipboardFiles(fileIn, clipboardOwner);
        } catch (IOException ex) {
            if (fileReceiver == Thread.currentThread()) {
                closeConnections();
            } else {
                Log.debug("Ignoring I/O error of a stale file receiver");
            }
        }
    }

    /**
     * May block (!)
     * <p/>
     * We're receiving a fully compressed (and ready to send over the network)
     * capture.
     */
    @Override
    public void onCompressed(int captureId, CompressionMethod compressionMethod, CompressorEngineConfiguration compressionConfiguration,
                             MemByteBuffer compressed) {
        if (sender != null) {
            sender.sendCapture(captureId, compressionMethod, compressionConfiguration, compressed);
        }
    }

    /**
     * May block (!)
     */
    @Override
    public boolean onLocationUpdated(Point location) {
        return sender == null || sender.sendMouseLocation(ScreenUtilities.toRelativeLocation(location));
    }

    public void sendResizeScreen(int width, int height) {
        if (sender != null) {
            sender.sendResizeScreen(width, height);
        }
    }

    public void farewell() {
        if (sender != null) {
            sender.sendGoodbye();
            pause(100);
        }
    }

    private void fireOnConnected(String fingerprints, boolean isRevertedConnection) {
        listeners.getListeners().forEach(listener -> listener.onConnected(fingerprints, isRevertedConnection));
    }

    @Override
    protected void fireOnClipboardReceived() {
        // let the assistant know that we're done
        sender.ping();
    }

    private void fireOnConnecting(NetworkAssistedEngineConfiguration configuration) {
        listeners.getListeners().forEach(listener -> listener.onConnecting(configuration.getServerName(), configuration.getServerPort()));
    }

    private void fireOnIceConnecting() {
        listeners.getListeners().forEach(NetworkAssistedEngineListener::onIceConnecting);
    }

    private void fireOnPeerIsAccessible(boolean isPeerAccessible) {
        listeners.getListeners().forEach(listener -> listener.onPeerIsAccessible(isPeerAccessible));
    }

    private void fireOnHostNotFound(NetworkAssistedEngineConfiguration configuration) {
        listeners.getListeners().forEach(listener -> listener.onHostNotFound(configuration.getServerName()));
    }

    private void fireOnConnectionTimeout(NetworkAssistedEngineConfiguration configuration) {
        listeners.getListeners().forEach(listener -> listener.onConnectionTimeout(configuration.getServerName(), configuration.getServerPort()));
    }

    private void fireOnRefused(NetworkAssistedEngineConfiguration configuration) {
        listeners.getListeners().forEach(listener -> listener.onRefused(configuration.getServerName(), configuration.getServerPort()));
    }

    private void fireOnDisconnecting() {
        listeners.getListeners().forEach(NetworkAssistedEngineListener::onDisconnecting);
    }

    @Override
    protected void fireOnIOError(IOException ex) {
        listeners.getListeners().forEach(listener -> listener.onIOError(ex));
    }

    private void fireOnAccepting(int port) {
        listeners.getListeners().forEach(listener -> listener.onAccepting(port));
    }

    private void fireOnReconfigured(NetworkAssistedEngineConfiguration configuration) {
        listeners.getListeners().forEach(listener -> listener.onReconfigured(configuration));
    }

}
