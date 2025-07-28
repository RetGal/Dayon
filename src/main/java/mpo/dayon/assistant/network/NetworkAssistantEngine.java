package mpo.dayon.assistant.network;

import com.dosse.upnp.UPnP;
import mpo.dayon.common.IceTest;
import mpo.dayon.common.SdpUtils;
import mpo.dayon.common.compressor.CompressorEngineConfiguration;
import mpo.dayon.common.capture.CaptureEngineConfiguration;
import mpo.dayon.common.concurrent.RunnableEx;
import mpo.dayon.common.configuration.ReConfigurable;
import mpo.dayon.common.event.Listeners;
import mpo.dayon.common.log.Log;
import mpo.dayon.common.network.NetworkEngine;
import mpo.dayon.common.network.Token;
import mpo.dayon.common.network.message.*;
import mpo.dayon.common.security.CustomTrustManager;
import mpo.dayon.common.version.Version;
import org.ice4j.Transport;
import org.ice4j.TransportAddress;
import org.ice4j.ice.Agent;
import org.ice4j.ice.IceMediaStream;
import org.ice4j.ice.KeepAliveStrategy;
import org.ice4j.ice.harvest.StunCandidateHarvester;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.awt.*;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.io.*;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.time.Duration;
import java.util.Base64;

import static java.lang.String.format;
import static java.lang.Thread.sleep;
import static mpo.dayon.common.configuration.Configuration.DEFAULT_TOKEN_SERVER_URL;
import static mpo.dayon.common.utils.SystemUtilities.safeClose;
import static mpo.dayon.common.version.Version.*;

public class NetworkAssistantEngine extends NetworkEngine implements ReConfigurable<NetworkAssistantEngineConfiguration> {

    private final NetworkCaptureMessageHandler captureMessageHandler;

    private final NetworkMouseLocationMessageHandler mouseMessageHandler;

    private final ClipboardOwner clipboardOwner;

    private final Listeners<NetworkAssistantEngineListener> listeners = new Listeners<>();

    private NetworkAssistantEngineConfiguration configuration;

    private SSLServerSocketFactory sssf;

    private SSLSocketFactory ssf;

    private boolean isInvertibleConnection;

    private volatile boolean hasRejected = false;

    private Token token;

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
                NetworkAssistantEngine.this.getReady(compatibilityMode);
            }
        }, "NetworkReceiver");
        receiver.start();
    }

    /**
     * Called from a GUI action => do not block the AWT thread (!)
     */
    public void cancel() {
        Log.info("Cancelling the network assistant engine...");
        if (sender != null && configuration.isTerminablePeer()) {
            sender.sendGoodbye();
        }
        cancelling.set(true);
        safeClose(server, connection, fileConnection);
        fireOnDisconnecting();
    }

    // right, keep streams open - forever!
    private void getReady(boolean compatibilityMode) throws NoSuchAlgorithmException, KeyManagementException {
        in = null;
        boolean introduced = false;
        boolean proceed = true;
        isInvertibleConnection = false;

        try {
            awaitConnections(compatibilityMode);
            startFileReceiver();
            initSender(8);
            createInputStream();
            if (isInvertibleConnection && hasRejected) {
                Log.debug("Inverted connection rejected by user");
                cancelling.set(true);
                proceed = false;
                hasRejected = false;
            } else if (isInvertibleConnection) {
                fireOnFingerprinted(CustomTrustManager.calculateFingerprints(connection.getSession(), this.getClass().getSimpleName()));
            }
            receivingLoop(proceed, introduced, compatibilityMode);
        } catch (IOException ex) {
            handleIOException(ex);
        } catch (CertificateEncodingException ex) {
            Log.error(ex.getMessage());
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException(e);
        } finally {
            closeConnections();
            UPnP.closePortTCP(configuration.getPort());
            fireOnReady();
        }
    }

    private void receivingLoop(boolean proceed, boolean introduced, boolean compatibilityMode) throws ClassNotFoundException, IOException, NoSuchAlgorithmException, KeyManagementException {
        // receiving loop
        try {
            while (proceed) {
                NetworkMessage.unmarshallMagicNumber(in); // blocking read (!)
                NetworkMessageType type = NetworkMessage.unmarshallEnum(in, NetworkMessageType.class);
                Log.debug("Received %s", type::name);
                if (introduced) {
                    proceed = processIntroduced(type, in);
                } else {
                    introduced = processUnIntroduced(type, in);
                }
            }
        } catch (IOException ex) {
            if (introduced && !cancelling.get()) {
                Log.warn("Session was interrupted - reconnect");
                fireOnSessionInterrupted();
                getReady(compatibilityMode);
            } else {
                throw ex;
            }
        }
    }

    private void awaitConnections(boolean compatibilityMode) throws NoSuchAlgorithmException, IOException, KeyManagementException, CertificateEncodingException {
        fireOnStarting(configuration.getPort(), isOwnPortAccessible.get());
        // if we can not expose our port, we check if the public ip of the assisted is available and if its port is accessible
        if (Boolean.FALSE.equals(isOwnPortAccessible.get()) && !compatibilityMode && token.getTokenString() != null) {
            fireOnCheckingPeerStatus(true);

            // DO ICE STUFF


            Agent agent = new Agent(); // A simple ICE Agent

            /*** Setup the STUN servers: ***/
            String[] hostnames = new String[]{"jitsi.org", "stun.ekiga.net"};
            // Look online for actively working public STUN Servers. You can find
            // free servers.
            // Now add these URLS as Stun Servers with standard 3478 port for STUN
            // servrs.
            for (String hostname : hostnames) {
                try {
                    // InetAddress qualifies a url to an IP Address, if you have an
                    // error here, make sure the url is reachable and correct
                    TransportAddress ta = new TransportAddress(InetAddress.getByName(hostname), 3478, Transport.UDP);
                    // Currently Ice4J only supports UDP and will throw an Error
                    // otherwise
                    agent.addCandidateHarvester(new StunCandidateHarvester(ta));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            /*
             * Now you have your Agent setup. The agent will now be able to know its
             * IP Address and Port once you attempt to connect. You do need to setup
             * Streams on the Agent to open a flow of information on a specific
             * port.
             */
            IceMediaStream stream = agent.createMediaStream("stream");
            int port = 5000; // Choose any port
            try {
                //agent.createComponent(stream, Transport.UDP, port, port, port + 100);
                agent.createComponent(stream, port, port, port + 100, KeepAliveStrategy.SELECTED_AND_TCP);
                // The three last arguments are: preferredPort, minPort, maxPort
            } catch (BindException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (IllegalArgumentException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            /*
             * Now we have our port and we have our stream to allow for information
             * to flow. The issue is that once we have all the information we need
             * each computer to get the remote computer's information. Of course how
             * do you get that information if you can't connect? There might be a
             * few ways, but the easiest with just ICE4J is to POST the information
             * to your public sever and retrieve the information. I even use a
             * simple PHP server I wrote to store and spit out information.
             */
            String toSend = null;
            try {
                toSend = SdpUtils.createSDPDescription(agent);
                toSend = Base64.getEncoder().encodeToString(toSend.getBytes(StandardCharsets.UTF_8));
                // Each computersends this information
                // This information describes all the possible IP addresses and
                // ports
            } catch (Throwable e) {
                e.printStackTrace();
            }
            queryPeerStatus(toSend);
            fireOnCheckingPeerStatus(false);
            isInvertibleConnection = isReverseConnectionPossible();
            if (isInvertibleConnection) {
                return;
            }

            if (token.getIceInfo() != null) {
                // ICE
                Log.info("ICE");

                String remoteReceived = new String(Base64.getDecoder().decode(token.getIceInfo()));
                Log.info(remoteReceived);

                try {
                    SdpUtils.parseSDP(agent, remoteReceived); // This will add the remote information to the agent.
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                //Hopefully now your Agent is totally setup. Now we need to start the connections:

                agent.addStateChangeListener(new IceTest.StateListener()); // We will define this class soon
                // You need to listen for state change so that once connected you can then use the socket.
                agent.startConnectivityEstablishment(); // This will do all the work for you to connect
            }




        } else if (Boolean.FALSE.equals(isOwnPortAccessible.get()) && (compatibilityMode || token.getTokenString() == null)) {
            Log.warn("Port not accessible from the outside, starting as server anyway");
            fireOnPeerIsAccessible(null, configuration.getPort(),false);
        }

        if (cancelling.get()) {
            throw new IOException("Cancelled");
        }
        // if we can expose our port, we start as server, wait for the assisted to connect and the assistant to accept
        startClassicMode(compatibilityMode);
    }

    private void startClassicMode(boolean compatibilityMode) throws NoSuchAlgorithmException, IOException, KeyManagementException, CertificateEncodingException {
        sssf = CustomTrustManager.initSslContext(compatibilityMode).getServerSocketFactory();
        Log.info(format("Dayon! server [port:%d]", configuration.getPort()));
        if (compatibilityMode) {
            Log.warn("Compatibility mode enabled, using legacy certificate");
        }
        if (server != null && server.isBound()) {
            safeClose(server);
        }
        server = (SSLServerSocket) sssf.createServerSocket(configuration.getPort());
        server.setNeedClientAuth(true);

        Log.info("Accepting...");
        do {
            if (connection != null && connection.isBound()) {
                safeClose(connection); // we might have refused the accepted connection (!)
            }
            connection = (SSLSocket) server.accept();
            Toolkit.getDefaultToolkit().beep();
            if (!connection.getSession().isValid()) {
                fireOnFingerprinted(null);
                throw new IOException("Certificate error, try enabling compatibility mode!");
            }
            Log.info(format("Incoming connection from %s", connection.getInetAddress().getHostAddress()));
        } while (!fireOnAccepted(connection, configuration.isAutoAccept()) && !cancelling.get());
        fireOnFingerprinted(CustomTrustManager.calculateFingerprints(connection.getSession(), this.getClass().getSimpleName()));

        if (server.isBound()) {
            safeClose(server);
        }
        server = null;
    }

    private boolean isReverseConnectionPossible() throws NoSuchAlgorithmException, IOException, KeyManagementException {
        if (Boolean.TRUE.equals(token.isPeerAccessible())) {
            int peerPort = token.getPeerPort();
            fireOnPeerIsAccessible(token.getPeerAddress(), peerPort, true);
            Log.info("Trying to connect to the assisted");
            ssf = CustomTrustManager.initSslContext(false).getSocketFactory();
            while (!connectToAssisted(token.getPeerAddress(), peerPort) && !cancelling.get()) {
                try {
                    sleep(2000);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
            hasRejected = !fireOnAccepted(connection, configuration.isAutoAccept());
            Log.info("Connected to the assisted");
            return true;
        }
        fireOnPeerIsAccessible(token.getPeerAddress(), configuration.getPort(), false);
        Log.warn("Assisted not accessible, starting as server");
        return false;
    }

    private void queryPeerStatus(String iceInfo) {
        String tokenServerUrl = configuration.getTokenServerUrl().isEmpty() ? DEFAULT_TOKEN_SERVER_URL : configuration.getTokenServerUrl();
        try {
            Log.info("Trying to obtain the assisted address");

            while (token.getPeerAddress() == null && !cancelling.get()) {
                obtainPeerAddressAndStatus(tokenServerUrl + token.getQueryParams(), !isOwnPortAccessible.get(), iceInfo);
                if (token.isPeerAccessible() == null) {
                    sleep(4000);
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

    private boolean connectToAssisted(String peerAddress, int peerPort) {
        Log.debug("Assisted address is " + peerAddress);
        try {
            connection = (SSLSocket) ssf.createSocket();
            connection.setNeedClientAuth(true);
            // grace period of 500 millis for the assisted to accept the connection
            connection.setSoTimeout(500);
            // abort the connection attempt after 5 seconds if the assisted cannot be reached
            connection.connect(new InetSocketAddress(peerAddress, peerPort), 4000);
            // once connected, remain connected until cancelled
            connection.setSoTimeout(0);
        } catch (IOException e) {
            Log.warn("Unable to connect to the assisted");
            return false;
        }
        return true;
    }

    private boolean initFileConnection(String peerAddress, int peerPort) {
        try {
            fileConnection = (SSLSocket) ssf.createSocket();
            // grace period of 500 millis for the assisted to accept the connection
            fileConnection.setSoTimeout(500);
            // abort the connection attempt after 5 seconds if the assistant cannot be reached
            fileConnection.connect(new InetSocketAddress(peerAddress, peerPort), 4000);
            // once connected, remain connected until cancelled
            fileConnection.setSoTimeout(0);
        } catch (IOException e) {
            Log.warn("Unable to connect to the assisted (file server)");
            return false;
        }
        return true;
    }

    private void obtainPeerAddressAndStatus(String tokenServerUrl, boolean closed, String iceInfo) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder().build();
        String query = format(tokenServerUrl, token.getTokenString(), closed ? 1 : 0, getLocalAddress());
        if (iceInfo != null) {
            query += "&ice=" + iceInfo;
        }
        Log.debug("Querying token server " + query);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(query))
                .timeout(Duration.ofSeconds(4))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        Log.debug("Got %s", () -> response.body().trim());
        String[] parts = response.body().trim().split("\\*");
        // ignore unknown closed status "-1"
        if (parts.length > 5 && !parts[4].isEmpty() && !parts[3].equals("-1")) {
            //   0 assistant 1 port 2 assistant_local 3 closed 4 rport 5 $assistant_ice
            token.updateToken(parts[0], Integer.parseInt(parts[1]), parts[2], !parts[3].equals("0"), Integer.parseInt(parts[4]), parts[5]);
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
    @SuppressWarnings({"squid:S2189", "squid:S2093"})
    private void fileReceivingLoop() {
        fileIn = null;
        Log.info(format("Dayon! file server [port:%d]", configuration.getPort()));

        try {
            if (isInvertibleConnection) {
                initFileConnection(token.getPeerAddress(), token.getPeerPort());
            } else {
                server = (SSLServerSocket) sssf.createServerSocket(configuration.getPort());
                fileConnection = (SSLSocket) server.accept();
                safeClose(server);
                server = null;
            }
            initFileSender();
            fileIn = new ObjectInputStream(new BufferedInputStream(fileConnection.getInputStream()));

            handleIncomingClipboardFiles(fileIn, clipboardOwner);
        } catch (IOException ex) {
            closeConnections();
        }
    }

    private boolean processIntroduced(NetworkMessageType type, ObjectInputStream in) throws IOException, ClassNotFoundException {
        switch (type) {
            case CAPTURE:
                final NetworkCaptureMessage capture = NetworkCaptureMessage.unmarshall(in);
                fireOnByteReceived(1 + capture.getWireSize()); // +1 : magic number (byte)
                captureMessageHandler.handleCapture(capture);
                return true;

            case MOUSE_LOCATION:
                final NetworkMouseLocationMessage mouse = NetworkMouseLocationMessage.unmarshall(in);
                fireOnByteReceived(1 + mouse.getWireSize()); // +1 : magic number (byte)
                mouseMessageHandler.handleLocation(mouse);
                return true;

            case CLIPBOARD_TEXT:
                final NetworkClipboardTextMessage clipboardTextMessage = NetworkClipboardTextMessage.unmarshall(in);
                fireOnByteReceived(1 + clipboardTextMessage.getWireSize()); // +1 : magic number (byte)
                setClipboardContents(clipboardTextMessage.getText(), clipboardOwner);
                fireOnClipboardReceived();
                return true;

            case CLIPBOARD_GRAPHIC:
                final NetworkClipboardGraphicMessage clipboardGraphicMessage = NetworkClipboardGraphicMessage.unmarshall(in);
                fireOnByteReceived(1 + clipboardGraphicMessage.getWireSize()); // +1 : magic number (byte)
                setClipboardContents(clipboardGraphicMessage.getGraphic().getTransferData(DataFlavor.imageFlavor), clipboardOwner);
                fireOnClipboardReceived();
                return true;

            case PING:
                fireOnClipboardSent();
                return true;

            case RESIZE:
                final NetworkResizeScreenMessage resize = NetworkResizeScreenMessage.unmarshall(in);
                fireOnByteReceived(1 + resize.getWireSize()); // +1 : magic number (byte)
                fireOnResizeScreen(resize.getWidth(), resize.getHeight());
                return true;

            case GOODBYE:
                fireOnTerminating();
                return false;

            case HELLO:
                throw new IllegalArgumentException("Unexpected message [HELLO]!");

            default:
                throw new IllegalArgumentException(format(UNSUPPORTED_TYPE, type));
        }
    }

    private boolean processUnIntroduced(NetworkMessageType type, ObjectInputStream in) throws IOException, ClassNotFoundException {
        switch (type) {
            case HELLO:
                fireOnConnected(connection, introduce(in));
                return true;

            case PING:
                return false;

            case CAPTURE:
            case MOUSE_LOCATION:
                // reconnect case
                processIntroduced(type, in);
                return false;

            case CLIPBOARD_TEXT:
            case CLIPBOARD_GRAPHIC:
            case CLIPBOARD_FILES:
            case GOODBYE:
                throw new IllegalArgumentException(format("Unexpected message [%s]!", type.name()));

            default:
                throw new IllegalArgumentException(format(UNSUPPORTED_TYPE, type));
        }
    }

    private NetworkHelloMessage introduce(ObjectInputStream in) throws IOException {
        final NetworkHelloMessage hello = NetworkHelloMessage.unmarshall(in);
        fireOnByteReceived(1 + hello.getWireSize()); // +1 : magic number (byte)
        if (!isCompatibleVersion(hello.getMajor(), hello.getMinor(), Version.get())) {
            Log.error(format("Incompatible assisted version: %d.%d", hello.getMajor(), hello.getMinor()));
            throw new IOException("version.wrong");
        }
        configuration.setMonochromePeer(!isColoredVersion(hello.getMajor()));
        configuration.setTerminablePeer(isTerminable(hello.getMajor()));
        return hello;
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
