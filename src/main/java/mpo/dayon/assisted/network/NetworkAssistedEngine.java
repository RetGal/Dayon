package mpo.dayon.assisted.network;

import com.dosse.upnp.UPnP;
import mpo.dayon.common.IceTest;
import mpo.dayon.common.SdpUtils;
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
import org.ice4j.Transport;
import org.ice4j.TransportAddress;
import org.ice4j.ice.*;
import org.ice4j.ice.Component;
import org.ice4j.ice.harvest.StunCandidateHarvester;
import org.ice4j.socket.IceSocketWrapper;

import javax.net.ssl.*;
import java.awt.*;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.io.*;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateEncodingException;
import java.time.Duration;
import java.util.Base64;
import java.util.SplittableRandom;
import java.util.logging.Level;

import static java.lang.String.format;

import static java.lang.Thread.sleep;
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

    private Agent iceAgent;

    private boolean iceConnected;

    private IceSocketWrapper iceSocketWrapper;

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

        if (token.getTokenString() != null && token.getPeerAddress() == null) {
            Log.debug("Incomplete Token, resolving " + token);
            // got public ip and able to expose a port?
            localPort = detectEnvironment();
            checkAndUpdateRVS(localPort, true);
            // TODO workaround
//            Log.debug("Updating configuration ServerName and ServerPort with Token values");
//            configuration.setServerName(token.getPeerAddress());
//            configuration.setServerPort(token.getPeerPort());
        }
        fireOnConnecting(configuration);

        // the assistant is not accessible check if reverting the connection initialisation is an option
        if (token.getTokenString() != null && Boolean.FALSE.equals(token.isPeerAccessible())) {
            fireOnPeerIsAccessible(false);
            Log.info("Assistant might not be accessible");
            if (token.getLocalPort() == 0) {
                // got public ip and able to expose a port?
                localPort = detectEnvironment();
                // update the rvs
                checkAndUpdateRVS(localPort, false);
            }
            Log.debug(String.valueOf(token));
//            // revert the connection and start server if necessary and possible
//            if (Boolean.TRUE.equals(isOwnPortAccessible.get()) && Boolean.FALSE.equals(token.isPeerAccessible())) {
//                localPort = token.getLocalPort();
//                fireOnAccepting(localPort);
//                startServer(localPort);
//                Log.debug("Connected");
//            } else {
//                isAssistantInSameNetwork = false; // TODO revert detectLocalAssistant();
//            }
        }
        // TODO REVERT
        //establishConnection(isAssistantInSameNetwork);
        establishConnection(false);
    }

    private void establishConnection(boolean isAssistantInSameNetwork) throws IOException, NoSuchAlgorithmException, CertificateEncodingException {
        // preferred case, we initiate the connection
        if (token.getTokenString() == null || token.isPeerAccessible() || isAssistantInSameNetwork) {
            fireOnPeerIsAccessible(true);
            Log.debug("Assistant is accessible");
            Log.info(format("Connecting to [%s:%s]...", configuration.getServerName(), configuration.getServerPort()));
            connectToAssistant(connectionTimeout);
        }

        if (token.getIceInfo() != null) {
            // ICE
            Log.info("ICE");

            initializeIceAgent();

            // Add state change listener
            iceAgent.addStateChangeListener(evt -> {
                IceProcessingState newState = (IceProcessingState) evt.getNewValue();
                Log.info("ICE state changed: " + newState);
                if (newState == IceProcessingState.TERMINATED) {
                    Log.info("ICE processing terminated");
                    Agent agent = (Agent) evt.getSource();
                    for (IceMediaStream stream: agent.getStreams()) {
                        if (stream.getName().contains("dayon")) {
                            Component rtpComponent = stream.getComponent(org.ice4j.ice.Component.RTP);
                            CandidatePair rtpPair = rtpComponent.getSelectedPair();
                            // We use IceSocketWrapper, but you can just use the UDP socket
                            // The advantage is that you can change the protocol from UDP to TCP easily
                            // Currently only UDP exists so you might not need to use the wrapper.
                            iceSocketWrapper = rtpPair.getIceSocketWrapper();
                            //IceSocketWrapper wrapper  = iceSocketWrapper;
                            // Get information about remote address for packet settings
                            TransportAddress ta = rtpPair.getRemoteCandidate().getTransportAddress();
                            Log.info("remote hostname: " + ta.getAddress());
                            Log.info("remote port: " + ta.getPort());
                            configuration.setServerName(ta.getHostName());
                            configuration.setServerPort(ta.getPort());

                            fireOnConnecting(configuration);
                            try {
                                connectToAssistant(3000);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                            iceConnected = true;
/*


                                DatagramPacket packet = new DatagramPacket(new byte[10000],10000);
                                packet.setAddress(ta.getAddress());
                                packet.setPort(ta.getPort());
                                try {
                                    Log.info("wrapper.send >>>>>>>>>>>> ");
                                    wrapper.send(packet);
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
*/


                        }
                    }

                } else if (newState == IceProcessingState.FAILED) {
                    Log.error("ICE processing failed");
                    //fireOnError("ICE connection failed");
                }
            });

            String remoteReceived = new String(Base64.getDecoder().decode(token.getIceInfo()));
            Log.debug(remoteReceived);
            processRemoteSdp(remoteReceived);

        }

        int i = 0;
        while (this.connection == null && i < 20) {
            Log.info(iceAgent.getState().toString()+ "Waiting for ICE " +i);
            pause(300L);
            i++;
        }
        if (this.connection == null) {
            Log.info("ICE failed - try revert connection");
            // revert the connection and start server if necessary and possible
            if (Boolean.TRUE.equals(isOwnPortAccessible.get()) && Boolean.FALSE.equals(token.isPeerAccessible())) {
                int localPort = token.getLocalPort();
                fireOnAccepting(localPort);
                try {
                    startServer(localPort);
                } catch (KeyManagementException e) {
                    throw new RuntimeException(e);
                }
                Log.debug("Connected");
            }
            //fireOnConnecting(configuration);
            //connectToAssistant(5000);
        } else {
            Log.info("ICE succeeded");
            Log.debug("ICE connection: " + connection + configuration);
        }
        //connectToAssistant(5000);

        // common part
        createInputStream();
        runReceiversIfNecessary();
        receiver.start();
        pause(100L);
        initSender(1);
        // the first message being sent to the assistant (e.g. version identification, locale and OS).
        sender.sendHello(osId);

        // only if we initiated the connection, we also need to establish a file connection
        if (token.getTokenString() == null || token.isPeerAccessible() || isAssistantInSameNetwork || iceConnected) {
            fileConnection = (SSLSocket) ssf.createSocket(configuration.getServerName(), configuration.getServerPort());
            Log.debug("File connection established");
        }

        // common part
        fireOnConnected(CustomTrustManager.calculateFingerprints(connection.getSession(), this.getClass().getSimpleName()));
        Log.info("Connected with the assistant!");
        initFileSender();
        createFileInputStream();
        fileReceiver.start();
    }

    private void processRemoteSdp(String remoteSdp) {
        try {
            SdpUtils.parseSDP(iceAgent, remoteSdp);
            iceAgent.startConnectivityEstablishment();
        } catch (Exception e) {
            Log.error("Failed to process remote SDP", e);
            //fireOnError("Failed to process remote connection details: " + e.getMessage());
        }
    }

    private void initializeIceAgent() {
        try {
            iceAgent = new Agent();
            iceAgent.setLoggingLevel(Level.INFO);

            // Add STUN servers
            String[] stunServers = {
                    "jitsi.org:3478",
                    "stun.ekiga.net:3478",
                    "stun.l.google.com:19302",
                    "stun1.l.google.com:19302",
                    "stun2.l.google.com:19302",
                    "stun3.l.google.com:19302",
                    "stun4.l.google.com:19302"
            };

            for (String server : stunServers) {
                try {
                    String[] parts = server.split(":");
                    TransportAddress ta = new TransportAddress(new InetSocketAddress(parts[0], Integer.parseInt(parts[1])), Transport.UDP);
                    iceAgent.addCandidateHarvester(new StunCandidateHarvester(ta));
                } catch (Exception e) {
                    Log.warn("Failed to add STUN server: " + server, e);
                }
            }

            // Add UPnP harvester if available
//            try {
//                iceAgent.addCandidateHarvester(Harvesters.getUPnPHarvester());
//            } catch (Exception e) {
//                Log.warn("UPnP harvester not available", e);
//            }


            // Component component = iceAgent.createComponent(Transport.UDP, 10000); // Local port


            // Create media stream
            int port = new SplittableRandom().nextInt(8000, 9000);

            IceMediaStream mediaStream = iceAgent.createMediaStream("dayon");
            iceAgent.createComponent(mediaStream, port, port, port, KeepAliveStrategy.SELECTED_AND_TCP);

        } catch (Exception e) {
            Log.error("Failed to initialize ICE agent", e);
            //fireOnError("Failed to initialize ICE: " + e.getMessage());
        }
    }

    private boolean detectLocalAssistant() {
        if (publicIp.equals(token.getPeerAddress())) {
            Log.debug("Connecting to the assistants local address");
            configuration.setServerName(token.getPeerLocalAddress());
            fireOnConnecting(configuration);
            // grace period for the assistant to get ready
            try {
                sleep(4000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
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
            final String connectionParams = resolveToken(tokenServerUrl + queryParams, token.getTokenString(), localPort, isOwnPortAccessible.get(), getLocalAddress(), null);
            String[] parts = connectionParams.split("\\*");
            if (parts.length > 1) {
                Log.debug("Length: " + parts.length);
                String assistantAddress = parts[0];
                String port = parts[1];
                if (parts.length > 5) {
                    // got ICE info
                    Log.info("ICE");
                    token.updateToken(assistantAddress, Integer.parseInt(port), parts[2], parts[3].equals("0"), localPort, parts[5]);
                } else {
                    Log.info("N-ICE");
                    // assuming peerAccessible is true
                    token.updateToken(assistantAddress, Integer.parseInt(port), "",true, 0, null);
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
        int portNumber = token.getLocalPort() != 0 ? token.getLocalPort() : configuration.getServerPort();
        if (!selfTest(publicIp, portNumber, remoteHost)) {
            // try a random port number if we couldn't open the one of the server
            portNumber = random.nextInt(8975) + 1025;
            if (selfTest(publicIp, portNumber, remoteHost)) {
                return portNumber;
            }
            return 0;
        }
        return configuration.getServerPort();
    }

    private void connectToAssistant(int connectionTimeout) throws IOException {
        try {
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
        
        // Final check to ensure connection is not null
        if (connection == null) {
            throw new IOException("Connection is null after connectToAssistant");
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

    public static String resolveToken(String tokenServerUrl, String token, int port, Boolean open, String localAddress, String iceInfo) throws IOException, InterruptedException {
        if (open == null) {
            isOwnPortAccessible.set(null);
        }
        // null = unknown = -1, true = open = 1, false = closed = 0
        String query = format(tokenServerUrl, token, port, toInt(open), localAddress);
        if (iceInfo != null) {
            query += "&ice=" + iceInfo;
        }
        Log.debug("Resolving token using: " + query);
        HttpClient client = HttpClient.newBuilder().build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(query))
                .timeout(Duration.ofSeconds(5))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
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

    private void fireOnConnected(String fingerprints) {
        listeners.getListeners().forEach(listener -> listener.onConnected(fingerprints));
    }

    @Override
    protected void fireOnClipboardReceived() {
        // let the assistant know that we're done
        sender.ping();
    }

    private void fireOnConnecting(NetworkAssistedEngineConfiguration configuration) {
        listeners.getListeners().forEach(listener -> listener.onConnecting(configuration.getServerName(), configuration.getServerPort()));
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
