package mpo.dayon.assisted.network;

import com.dosse.upnp.UPnP;
import mpo.dayon.common.network.SdpUtils;
import mpo.dayon.common.compressor.CompressorEngineConfiguration;
import mpo.dayon.common.compressor.CompressorEngineListener;
import mpo.dayon.assisted.control.NetworkControlMessageHandler;
import mpo.dayon.assisted.mouse.MouseEngineListener;
import mpo.dayon.common.buffer.MemByteBuffer;
import mpo.dayon.common.concurrent.RunnableEx;
import mpo.dayon.common.configuration.ReConfigurable;
import mpo.dayon.common.error.FatalErrorHandler;
import mpo.dayon.common.event.Listeners;
import mpo.dayon.common.log.Log;
import mpo.dayon.common.network.NetworkEngine;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.Base64;
import java.util.SplittableRandom;

import static java.lang.String.format;

import static mpo.dayon.common.configuration.Configuration.DEFAULT_TOKEN_SERVER_URL;
import static mpo.dayon.common.utils.SystemUtilities.*;

public class NetworkAssistedEngine extends NetworkEngine
        implements ReConfigurable<NetworkAssistedEngineConfiguration>, CompressorEngineListener, MouseEngineListener {
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

    private Token token;

    private int connectionTimeout;

    private SSLSocketFactory ssf;

    private String publicIp;

    private IceMediaStream mediaStream;

    private boolean iceConnected;

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
        this.token = token;
        this.connectionTimeout = connectionTimeout;
        try {
            start();
        } catch (UnknownHostException e) {
            fireOnHostNotFound(configuration);
        } catch (SocketTimeoutException e) {
            fireOnConnectionTimeout(configuration);
        } catch (IOException e) {
            closeConnections();
            fireOnRefused(configuration);
        } catch (NoSuchAlgorithmException | KeyManagementException | CertificateEncodingException e) {
            FatalErrorHandler.bye(e.getMessage(), e);
        } finally {
            if (token.getLocalPort() != 0) {
                UPnP.closePortTCP(token.getLocalPort(), token.getPeerAddress());
            }
        }
    }

    @SuppressWarnings("java:S2095") // our sockets MUST NOT be closed
    private void start() throws IOException, NoSuchAlgorithmException, KeyManagementException, CertificateEncodingException {
        Log.debug(token.toString());
        ssf = CustomTrustManager.initSslContext(false).getSocketFactory();
        int localPort;
        boolean isAssistantInSameNetwork = false;
        boolean isRevertedConnection = false;

        if (token.getTokenString() != null && token.getPeerAddress() == null) {
            Log.debug("Incomplete Token, resolving " + token);
            // got public ip and able to expose a port?
            localPort = detectEnvironment();
            checkAndUpdateRVS(localPort, true);
            if (token.getPeerAddress() == null || token.getPeerPort() == 0) {
                Log.warn("Token resolution failed");
                return;
            }
            Log.debug("Updating configuration ServerName and ServerPort with Token values");
            configuration.setServerName(token.getPeerAddress());
            configuration.setServerPort(token.getPeerPort());
            configuration.persist();
        }
        fireOnConnecting(configuration);

        // the assistant is not accessible check if reverting the connection initialization is an option
        if (token.getTokenString() != null && Boolean.FALSE.equals(token.isPeerAccessible())) {
            fireOnPeerIsAccessible(false);
            Log.info("Assistant is not accessible directly");
            if (token.getLocalPort() == 0) {
                // got public ip and able to expose a port?
                localPort = detectEnvironment();
                // update the rvs
                checkAndUpdateRVS(localPort, false);
            }
            Log.debug(String.valueOf(token));
            Log.debug("Updating configuration ServerName and ServerPort with Token values");
            configuration.setServerName(token.getPeerAddress());
            configuration.setServerPort(token.getPeerPort());
            // revert the connection and start server if necessary and possible
            if (Boolean.TRUE.equals(isOwnPortAccessible.get()) && Boolean.FALSE.equals(token.isPeerAccessible())) {
                Log.info("Reverting the connection initialization");
                localPort = token.getLocalPort();
                fireOnAccepting(localPort);
                startServer(localPort);
                Log.debug("Connected");
                isRevertedConnection = true;
            } else if (token.getIceInfo() == null) {
                // last ressort for legacy, non-ice, non-token connections
                isAssistantInSameNetwork = detectLocalAssistant();
            }
        }
        establishConnection(isAssistantInSameNetwork, isRevertedConnection);
    }

    private void establishConnection(boolean isAssistantInSameNetwork, boolean isRevertedConnection) throws IOException, NoSuchAlgorithmException, CertificateEncodingException {
        // preferred case, we initiate the connection
        if (token.getTokenString() == null || token.isPeerAccessible() || (isAssistantInSameNetwork && token.getIceInfo() == null)) {
            fireOnPeerIsAccessible(true);
            if (token.getTokenString() != null && token.isPeerAccessible()) {
                configuration.setServerName(token.getPeerAddress());
                configuration.setServerPort(token.getPeerPort());
            }
            Log.info(format("Connecting to [%s:%s]...", configuration.getServerName(), configuration.getServerPort()));
            connectToAssistant(connectionTimeout, 0);
        }

        // ICE connection
        if (connection == null && token.getIceInfo() != null) {
            fireOnIceConnecting();
            connectIce();
        }

        // common part
        createInputStream();
        runReceiversIfNecessary();
        receiver.start();
        pause(100L);
        initSender(1);
        // the first message being sent to the assistant (e.g. version identification, locale and OS).
        sender.sendHello(osId);

        // only if we initiated the connection, we also need to establish a file connection
        if (!isRevertedConnection) {
            fileConnection = (SSLSocket) ssf.createSocket(configuration.getServerName(), configuration.getServerPort());
            Log.debug("File connection established");
        }

        // common part
        fireOnConnected(CustomTrustManager.calculateFingerprints(connection.getSession(), this.getClass().getSimpleName()), isRevertedConnection);
        Log.info("Connected with the assistant!");
        initFileSender();
        createFileInputStream();
        fileReceiver.start();
    }

    private void connectIce() throws IOException {
        Log.info("Starting ICE");
        initIceAgent();
        getLocalSdpDesc();
        // process remote SDP and start connectivity establishment
        processRemoteSdp(new String(Base64.getDecoder().decode(token.getIceInfo())));
        // wait for ICE connection using an event-driven latch with a bounded timeout
        final CountDownLatch latch = new CountDownLatch(1);
        // attach a combined state change listener that both updates state and signals the latch
        monitorIceProcessingState(latch);

        final long start = System.nanoTime();
        final long timeoutMs = 5000L; // total wait for ICE
        try {
            boolean signaled = latch.await(timeoutMs, TimeUnit.MILLISECONDS);
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            Log.debug("ICE wait elapsed: " + elapsed + "ms, signaled=" + signaled + ", iceConnected=" + iceConnected);
            if (connection == null || !iceConnected) {
                Log.info("ICE connection failed");
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

    private void monitorIceProcessingState(CountDownLatch latch) {
        iceAgent.addStateChangeListener(evt -> {
            IceProcessingState newState = (IceProcessingState) evt.getNewValue();
            Log.debug("ICE state changed: " + newState);
            switch (newState) {
                case COMPLETED:
                    iceAgent.free();
                    break;
                case TERMINATED:
                    handleIceTerminated(evt);
                    break;
                case FAILED:
                    iceConnected = false;
                    Log.error("ICE processing failed");
                    break;
                default:
                    break;
            }
            if (latch != null && (newState == IceProcessingState.FAILED || newState == IceProcessingState.TERMINATED)) {
                latch.countDown();
            }
        });
    }

    private void handleIceTerminated(PropertyChangeEvent evt) {
        Log.info("ICE processing terminated");
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

        TransportAddress ta = rtpPair.getRemoteCandidate().getTransportAddress();
        configuration.setServerName(ta.getHostName());
        configuration.setServerPort(ta.getPort());
        fireOnConnecting(configuration);
        try {
            connectToAssistant(3000, 250);
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

    private void initIceAgent() {
        super.initializeIceAgent();
        createMediaStream();
    }

    private void createMediaStream() {
        if (mediaStream != null) {
            iceAgent.removeStream(mediaStream);
        }
        mediaStream = iceAgent.createMediaStream("dayon");
        int port = new SplittableRandom().nextInt(8000, 9000);
        try {
            iceAgent.createComponent(mediaStream, port, port, port, KeepAliveStrategy.SELECTED_AND_TCP);
        } catch (IOException e) {
            Log.error("Failed to initialize ICE agent", e);
        }
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
        SSLServerSocketFactory sssf;
        try {
            sssf = CustomTrustManager.initSslContext(false).getServerSocketFactory();
            Log.info(format("Dayon! server [port:%d]", port));
            server = (SSLServerSocket) sssf.createServerSocket(port);
            server.setNeedClientAuth(true);
            Log.info("Accepting...");
            connection = (SSLSocket) server.accept();
            Toolkit.getDefaultToolkit().beep();
            Log.info(format("Incoming connection from %s", connection.getInetAddress().getHostAddress()));
        } catch (IOException e) {
            Log.error("Error accepting incoming connection", e);
            closeConnections();
            return;
        }

        try {
            fileConnection = (SSLSocket) server.accept();
            safeClose(server);
            Log.debug("File connection established");
        } catch (IOException e) {
            Log.error("Error establishing file connection", e);
            closeConnections();
        }
    }

    private void checkAndUpdateRVS(int localPort, boolean incomplete) throws IOException {
        try {
            String queryParams = incomplete? token.getQueryParams() + "&inc" : token.getQueryParams();
            String tokenServerUrl = configuration.getTokenServerUrl().isEmpty() ? DEFAULT_TOKEN_SERVER_URL : configuration.getTokenServerUrl();
            final String connectionParams = resolveToken(tokenServerUrl + queryParams, token.getTokenString(), localPort, isOwnPortAccessible.get(), getLocalAddress());
            String[] parts = connectionParams.split("\\*");
            if (parts.length > 1) {
                Log.debug("Length: " + parts.length);
                String assistantAddress = parts[0];
                String port = parts[1];
                if (parts.length > 5) {
                    Log.debug("Received ICE information");
                    // legacy RVS - should actually never receive this
                    //   0              1       2            3          4           5       6      7
                    // 188.155.141.250*8090*192.168.1.40*1786374622*188.155.141.250*0*192.168.1.40*0
                    // v.1.6 RVS
                    //   0              1       2            3      4     5
                    // 188.155.141.250*8090*192.168.1.40*1786374911*0*dj0wDQpvPWljZTRqLm9yZyAwIDAgSU4gSVA0IDE4OC4xNTU
                    // part 3 (closed) is 0 or timestamp -> 0 means assistant is accessible
                    token.updateToken(assistantAddress, Integer.parseInt(port), parts[2], parts[3].equals("0"), localPort, parts[5]);
                } else {
                    Log.debug("Received no ICE information");
                    // legacy RVS
                    //   0              1       2            3       4
                    // 188.155.141.250*8056*192.168.1.135*1786705306*0*
                    token.updateToken(assistantAddress, Integer.parseInt(port), parts[2], parts[3].equals("0"), 0, null);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private int detectEnvironment() {
        if (publicIp == null) {
            publicIp = resolvePublicIp();
        }
        String remoteHost = configuration.getServerName();
        // reuse the port number if possible
        int portNumber = token.getLocalPort() != 0 ? token.getLocalPort() : random.nextInt(8975) + 1025;
        if (!selfTest(publicIp, portNumber, remoteHost)) {
            return 0;
        }
        return configuration.getServerPort();
    }

    private void connectToAssistant(int connectionTimeout, int preDelay) throws IOException {
        try {
            if (preDelay > 0) {
                pause(preDelay);
            }
            Log.info("Connecting to assistant " + configuration.getServerName() + ":" + configuration.getServerPort());
            connection = (SSLSocket) ssf.createSocket();
            connection.setNeedClientAuth(true);
            // grace period of twice the connection timeout (default 14 seconds) for the assistant to accept the connection
            connection.setSoTimeout(2*connectionTimeout);
            // abort the connection attempt after connection timeout (default 7 seconds) if the assistant cannot be reached
            connection.connect(new InetSocketAddress(configuration.getServerName(), configuration.getServerPort()), connectionTimeout);
            // once connected, remain connected until cancelled
            connection.setSoTimeout(0);
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
    }

    private void runReceiversIfNecessary() {
        if (receiver == null) {
            Log.info("Getting the receivers ready");
            runReceivers();
        }
    }

    public static String resolveToken(String tokenServerUrl, String token, int port, Boolean open, String localAddress) throws IOException, InterruptedException {
        if (open == null) {
            isOwnPortAccessible.set(null);
        }
        // null = unknown = -1, true = open = 1, false = closed = 0
        String query = format(tokenServerUrl, token, port, toInt(open), localAddress);
        Log.debug("Resolving token using: " + query);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(query))
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofSeconds(5))
                .build();
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
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
        cancelling.set(true);
        if (iceAgent != null) {
            iceAgent.free();
        }
        closeConnections();
        fireOnDisconnecting();
    }

    private void receivingLoop() {

        try {
            //noinspection InfiniteLoopStatement
            while (true) {
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
                        setClipboardContents(NetworkClipboardTextMessage.unmarshall(in).getText(), clipboardOwner);
                        sender.ping();
                        break;
                    case CLIPBOARD_GRAPHIC:
                        setClipboardContents(NetworkClipboardGraphicMessage.unmarshall(in).getGraphic().getTransferData(DataFlavor.imageFlavor), clipboardOwner);
                        sender.ping();
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
                        throw new IllegalArgumentException(format(UNSUPPORTED_TYPE, type));
                }
            }
        } catch (IOException ex) {
            if (!cancelling.get()) {
                closeConnections();
                pause(1500);
                Log.warn("Session was interrupted - reconnect");
                connect(token, connectionTimeout);
            } else {
                closeConnections();
                Log.info("Stopped network receiver (cancelled)");
                fireOnDisconnecting();
            }
        } catch (ClassNotFoundException e) {
            closeConnections();
            fireOnDisconnecting();
            throw new IllegalArgumentException(e);
        }
    }

    private void fileReceivingLoop() {
        try {
            handleIncomingClipboardFiles(fileIn, clipboardOwner);
        } catch (IOException ex) {
            closeConnections();
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
        return sender == null || sender.sendMouseLocation(location);
    }

    public void sendResizeScreen(int width, int height) {
        if (sender != null) {
            sender.sendResizeScreen(width, height);
        }
    }

    public void farewell() {
        if (sender != null) {
            sender.sendGoodbye();
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
