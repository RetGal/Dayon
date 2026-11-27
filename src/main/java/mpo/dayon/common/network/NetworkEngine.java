package mpo.dayon.common.network;

import com.dosse.upnp.UPnP;
import mpo.dayon.common.log.Log;
import mpo.dayon.common.network.message.*;
import mpo.dayon.common.network.transport.SessionTransport;
import mpo.dayon.common.network.transport.ice.IceSessionTransport;
import mpo.dayon.common.security.CustomTrustManager;
import org.ice4j.Transport;
import org.ice4j.TransportAddress;
import org.ice4j.ice.Agent;
import org.ice4j.ice.Component;
import org.ice4j.ice.IceMediaStream;
import org.ice4j.ice.IceProcessingState;
import org.ice4j.ice.NominationStrategy;
import org.ice4j.ice.harvest.StunCandidateHarvester;
import org.ice4j.ice.harvest.TurnCandidateHarvester;
import org.ice4j.security.LongTermCredential;

import javax.net.ssl.SSLServerSocket;
import javax.sdp.SdpException;
import java.awt.*;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static mpo.dayon.common.network.message.NetworkMessageType.CLIPBOARD_FILES;
import static mpo.dayon.common.network.message.NetworkMessageType.PING;
import static mpo.dayon.common.utils.SystemUtilities.*;

/**
 * Both the assistant and the assisted are talking to each other using a very
 * simple asynchronous network message layer. The network engine is handling
 * both the sending and the receiving sides.
 */
public abstract class NetworkEngine {

    public static final String USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64; rv:136.0) Gecko/20100101 Firefox/136.0";

    public static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().proxy(ProxySelector.getDefault()).build();

    protected static final String UNSUPPORTED_TYPE = "Unsupported message type [%s]!";

    protected static final long ICE_TIMEOUT_MS = 10_000L;

    protected static final long ICE_INFO_WAIT_MS = 20_000L;

    protected static final long ICE_INFO_POLL_INTERVAL_MS = 2_000L;

    private static final String CLIPBOARD_DEBUG = "setClipboardContents %s";

    private static final String WHATSMYIP_SERVER_URL = "https://fensterkitt.ch/dayon/whatismyip.php";

    // TURN server entry: host:port[:user[:pass]] or [ipv6]:port[:user[:pass]]
    private static final Pattern TURN_ENTRY = Pattern.compile("^(?:\\[([^\\]]+)]|([^:\\[\\]]+)):(\\d{1,5})(?::([^:]*)(?::(.*))?)?$");

    private static final String[][] STUN_SERVERS = {
        {"stun.nextcloud.com", "443"},
        {"stun.freeswitch.org", "3478"},
        {"stun.gigaset.net", "3478"},
        {"stun.gmx.net", "3478"}
    };

    protected NetworkSender sender; // out

    private NetworkSender fileSender; // file out

    protected Thread receiver; // in

    protected ObjectInputStream in;

    protected Thread fileReceiver; // file in

    protected ObjectInputStream fileIn;

    protected SSLServerSocket server;

    protected SessionTransport connection;

    protected SessionTransport fileConnection;

    protected Agent iceAgent;

    protected IceMediaStream mediaStream;

    protected String consumedPeerIceInfo;

    protected final AtomicBoolean cancelling = new AtomicBoolean(false);

    private final Object upnpEnabledLOCK = new Object();

    private Boolean upnpEnabled;

    public static AtomicReference<Boolean> isOwnPortAccessible = new AtomicReference<>();

    private String localAddress = null;

    private byte[] mainPeerCertificateFingerprint;

    private byte[] expectedReconnectPeerFingerprint;

    /**
     * Might be blocking if the sender queue is full (!)
     */
    public void sendClipboardText(String text) {
        if (sender != null) {
            String utf8Encoded = UTF_8.decode(UTF_8.encode(text)).toString();
            sender.sendClipboardContentText(utf8Encoded, utf8Encoded.getBytes().length);
        }
    }

    /**
     * Might be blocking if the sender queue is full (!)
     */
    public void sendClipboardGraphic(TransferableImage image) {
        if (sender != null) {
            sender.sendClipboardContentGraphic(image);
        }
    }

    /**
     * Might be blocking if the sender queue is full (!)
     *
     * @return true when the files could be sent, false when no readable file was left
     */
    public boolean sendClipboardFiles(List<File> files, long size, String basePath) {
        if (fileSender != null) {
            return fileSender.sendClipboardContentFiles(files, size, basePath);
        }
        return false;
    }

    public void sendClipboardTransferComplete() {
        if (sender != null) {
            sender.ping();
        }
    }

    protected static void setClipboardContents(String string, ClipboardOwner clipboardOwner) {
        Log.debug(CLIPBOARD_DEBUG, () -> string);
        StringSelection stringSelection = new StringSelection(string);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringSelection, clipboardOwner);
    }

    public static void setClipboardContents(BufferedImage image, ClipboardOwner clipboardOwner) {
        Log.debug(CLIPBOARD_DEBUG, () -> format("%dx%d", image.getWidth(), image.getHeight()));
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new TransferableImage(image), clipboardOwner);
    }

    private static void setClipboardContents(List<File> files, ClipboardOwner clipboardOwner) {
        Log.debug(CLIPBOARD_DEBUG, () -> String.valueOf(files));
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new TransferableFiles(files), clipboardOwner);
    }

    private static NetworkClipboardFilesHelper handleNetworkClipboardFilesHelper(NetworkClipboardFilesHelper filesHelper, ClipboardOwner clipboardOwner) {
        if (filesHelper.isDone()) {
            filesHelper.close();
            setClipboardContents(filesHelper.getFiles(), clipboardOwner);
            return new NetworkClipboardFilesHelper();
        }
        return filesHelper;
    }

    protected void initSender(int queueSize) throws IOException {
        sender = new NetworkSender(new ObjectOutputStream(new BufferedOutputStream(connection.getOutputStream())));
        sender.start(queueSize);
        sender.ping();
    }

    protected void initFileSender() throws IOException {
        fileSender = new NetworkSender(new ObjectOutputStream(new BufferedOutputStream(fileConnection.getOutputStream())));
        fileSender.start(1);
        fileSender.ping();
    }

    // remembers the peer certificate of the authenticated main connection so that the file transfer connection can be bound to the very same peer
    protected void rememberMainPeerIdentity() {
        mainPeerCertificateFingerprint = connection == null ? null : connection.getPeerCertificateFingerprint();
        if (mainPeerCertificateFingerprint == null) {
            Log.warn("Could not determine the identity of the main connection");
        }
    }

    // a file connection is only accepted if its peer certificate matches the one of the main connection.
    protected boolean isFileConnectionBound(SessionTransport candidate) {
        if (mainPeerCertificateFingerprint == null) {
            Log.warn("Rejecting file connection: the identity of the main connection is unknown");
            return false;
        }
        final byte[] filePeerCertificateFingerprint = candidate == null ? null : candidate.getPeerCertificateFingerprint();
        if (filePeerCertificateFingerprint == null) {
            Log.warn("Rejecting file connection: no peer certificate available");
            return false;
        }
        return MessageDigest.isEqual(mainPeerCertificateFingerprint, filePeerCertificateFingerprint);
    }

    // remembers the identity of the current peer so that a reconnect can be bound to the very same peer
    protected void rememberPeerIdentityForReconnect() {
        expectedReconnectPeerFingerprint = mainPeerCertificateFingerprint == null
                ? null
                : mainPeerCertificateFingerprint.clone();
    }

    protected void clearPeerIdentityForReconnect() {
        expectedReconnectPeerFingerprint = null;
    }

    protected boolean hasPeerIdentityForReconnect() {
        return expectedReconnectPeerFingerprint != null;
    }

    // a reconnect is only accepted if the peer certificate matches the one of the interrupted session exactly
    protected boolean isExpectedReconnectPeer(SessionTransport candidate) {
        if (expectedReconnectPeerFingerprint == null || candidate == null) {
            return false;
        }
        final byte[] candidateFingerprint = candidate.getPeerCertificateFingerprint();
        return candidateFingerprint != null
                && MessageDigest.isEqual(expectedReconnectPeerFingerprint, candidateFingerprint);
    }

    protected void handleIncomingClipboardFiles(ObjectInputStream fileIn, ClipboardOwner clipboardOwner) throws IOException {
        String tmpDir = getTempDir();
        NetworkClipboardFilesHelper filesHelper = new NetworkClipboardFilesHelper();

        try {
            //noinspection InfiniteLoopStatement
            while (true) {
                try {
                    NetworkMessageType type;
                    if (filesHelper.isDone()) {
                        NetworkMessage.unmarshallMagicNumber(fileIn); // blocking read (!)
                        type = NetworkMessage.unmarshallEnum(fileIn, NetworkMessageType.class);
                        Log.debug("Received " + type.name());
                        if (!type.equals(CLIPBOARD_FILES) && !type.equals(PING)) {
                            throw new IOException(format(UNSUPPORTED_TYPE, type));
                        }
                    } else {
                        type = CLIPBOARD_FILES;
                    }

                    if (type.equals(CLIPBOARD_FILES)) {
                        filesHelper = handleNetworkClipboardFilesHelper(NetworkClipboardFilesMessage.unmarshall(fileIn,
                                filesHelper, tmpDir), clipboardOwner);
                        if (filesHelper.isDone()) {
                            fireOnClipboardReceived();
                        }
                    }
                } catch (RuntimeException ex) {
                    throw new IOException("Malformed clipboard file transfer", ex);
                }
            }
        } finally {
            filesHelper.close();
        }
    }

    protected void fireOnClipboardReceived() {
    }

    // closes all connections AND resets the cancelling flag
    protected void closeConnections() {
        closeConnections(true);
    }

    /**
     * Closes all connections. Between two reconnect attempts the cancelling flag
     * and the pinned peer identity have to be preserved.
     *
     * @param finalCleanup true when no reconnect will follow
     */
    protected void closeConnections(boolean finalCleanup) {
        if (sender != null) {
            sender.cancel();
        }
        receiver = closeThread(receiver, finalCleanup);
        safeClose(in, connection, server);

        if (fileSender != null) {
            fileSender.cancel();
        }
        // Detach the file receiver before closing its socket so that a stale receiver cannot tear down a newer session.
        final Thread oldFileReceiver = fileReceiver;
        fileReceiver = null;
        if (oldFileReceiver != null && oldFileReceiver != Thread.currentThread()) {
            safeInterrupt(oldFileReceiver);
        }
        safeClose(fileIn, fileConnection);
        mainPeerCertificateFingerprint = null;
        if (finalCleanup) {
            expectedReconnectPeerFingerprint = null;
            cancelling.set(false);
        }
    }

    // interrupts the given thread unless we are running on it ourselves
    private static Thread closeThread(Thread thread, boolean finalCleanup) {
        if (thread == null) {
            return null;
        }
        if (thread == Thread.currentThread()) {
            return finalCleanup ? null : thread;
        }
        return safeInterrupt(thread);
    }

    protected void createInputStream() throws IOException {
        if (connection == null) {
            throw new IOException("Connection not established");
        }
        try {
            in = new ObjectInputStream(new BufferedInputStream(connection.getInputStream()));
            NetworkClipboardGraphicMessage.installImageObjectInputFilter(in);
        } catch (StreamCorruptedException ex) {
            throw new IOException("version.wrong");
        }
    }

    protected void handleIOException(IOException ex) {
        if (!cancelling.get()) {
            Log.error("IO error (not cancelled)", ex);
            fireOnIOError(ex);
        } else {
            Log.info("Stopped network receiver (cancelled)");
        }
    }

    protected void fireOnIOError(IOException error) {
    }

    public static String nullToZeroStr(String str) {
        return str == null ? "0" : str;
    }

    public String getLocalAddress() {
        if (localAddress == null) {
            localAddress = obtainLocalAddress();
        }
        return localAddress;
    }

    public String resolvePublicIp() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(WHATSMYIP_SERVER_URL))
                    .header("User-Agent", USER_AGENT)
                    .timeout(Duration.ofSeconds(5))
                    .build();
            return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString()).body().trim();
        } catch (IOException | InterruptedException | SecurityException ex) {
            Log.error("Could not determine public IP", ex);
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
        return null;
    }

    // faster but less reliable as it requires a router supporting NAT reflection
    @SuppressWarnings("java:S1144")
    private boolean isPortAccessible(String publicIp, int portNumber) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(publicIp, portNumber), 1000);
            return true;
        }
    }

    private boolean isPortAccessible(int portNumber) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(format("%s/?p=%d", WHATSMYIP_SERVER_URL, portNumber)))
                    .header("User-Agent", USER_AGENT)
                    .timeout(Duration.ofSeconds(5))
                    .build();
            return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString()).body().trim().equals("1");
        } catch (IOException | InterruptedException | SecurityException ex) {
            Log.error("Could not determine port status", ex);
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
        return false;
    }

    // creates unrestricted port forwarding rule and performs a port accessibility check
    public boolean selfTest(String publicIp, int newPort) {
        return selfTest(publicIp, newPort, 0, null);
    }

    // creates unrestricted port forwarding rule and performs a port accessibility check
    public boolean selfTest(String publicIp, int newPort, int oldPort) {
        return selfTest(publicIp, newPort, oldPort, null);
    }

    // creates port forwarding rule for the specific remote host only and performs a port accessibility check
    protected boolean selfTest(String publicIp, int newPort, int oldPort, String remoteHost) {
        if (publicIp == null) {
            isOwnPortAccessible.set(false);
            return false;
        }
        manageRouterPorts(oldPort, newPort, remoteHost);
        boolean accessible;
        try (ServerSocket ignored = new ServerSocket(newPort)) {
            accessible = isPortAccessible(newPort);
        } catch (IOException e) {
            accessible = false;
        }
        if (!accessible) {
            Log.warn("Port " + newPort + " is not reachable from the outside");
            isOwnPortAccessible.set(false);
            return false;
        }
        Log.debug("Port " + newPort + " is reachable from the outside");
        isOwnPortAccessible.set(true);
        return true;
    }

    private String obtainLocalAddress() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("fensterkitt.ch", 80), 3000);
            return socket.getLocalAddress().getHostAddress();
        } catch (IOException e) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("info.cern.ch", 80), 4000);
                return socket.getLocalAddress().getHostAddress();
            } catch (IOException ex) {
                Log.warn("No internet connection");
                return null;
            }
        }
    }

    public static boolean manageRouterPorts(int oldPort, int newPort, String remoteHost) {
        if (!UPnP.isUPnPAvailable()) {
            return false;
        }
        if (oldPort != 0 && UPnP.isMappedTCP(oldPort)) {
            UPnP.closePortTCP(oldPort);
            Log.info(format("Disabled forwarding for port %d", oldPort));
        }
        if (!UPnP.isMappedTCP(newPort)) {
            if (UPnP.openPortTCP(newPort, remoteHost, "Dayon!")) {
                Log.info(format("Enabled forwarding for port %d", newPort));
                return true;
            }
            Log.warn(format("Failed to enable forwarding for port %d", newPort));
            return false;
        }
        return true;
    }

    public CompletableFuture<Boolean> isUpnpEnabled() {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (upnpEnabledLOCK) {
                while (upnpEnabled == null) {
                    try {
                        upnpEnabledLOCK.wait(5000);
                    } catch (InterruptedException e) {
                        Log.warn("Swallowed", e);
                        Thread.currentThread().interrupt();
                    }
                }
                return upnpEnabled;
            }
        });
    }

    public void initUpnp() {
        synchronized (upnpEnabledLOCK) {
            CompletableFuture.supplyAsync(UPnP::isUPnPAvailable).thenApply(enabled -> {
                Log.info(format("UPnP is %s", enabled.booleanValue() ? "enabled" : "disabled"));
                upnpEnabled = enabled;
                return enabled;
            });
            upnpEnabledLOCK.notifyAll();
        }
    }

    protected void initializeIceAgent(boolean controlling) {
        if (iceAgent == null) {
            Log.debug("Initializing new ICE agent (controlling=" + controlling + ")");
            iceAgent = new Agent();
            iceAgent.setLoggingLevel(Level.FINEST);
            iceAgent.setControlling(controlling);
            // prefer host/server-reflexive pairs because they have the lowest latency
            // use relayed pairs as fallback when no direct pair works
            iceAgent.setNominationStrategy(NominationStrategy.NOMINATE_FIRST_HOST_OR_REFLEXIVE_VALID);
            for (String[] sts : STUN_SERVERS) {
                try {
                    TransportAddress ta = new TransportAddress(new InetSocketAddress(sts[0], Integer.parseInt(sts[1])), Transport.UDP);
                    iceAgent.addCandidateHarvester(new StunCandidateHarvester(ta));
                } catch (Exception e) {
                    Log.warn("Failed to add STUN harvester: " + sts[0] + ":" + sts[1], e);
                }
            }
            Log.debug("Number of STUN harvesters: " + iceAgent.getHarvesters().size());
            addTurnServers();
        }
    }

    private void addTurnServers() {
        final String turns = mpo.dayon.common.preference.Preferences.getPreferences().getStringPreference("ice.turn.servers", "");
        final List<TurnServer> servers = parseTurnServers(turns);
        if (servers.isEmpty()) {
            return;
        }
        Log.info("Configuring TURN servers from preferences");
        for (TurnServer turnServer : servers) {
            try {
                final TransportAddress ta = new TransportAddress(new InetSocketAddress(turnServer.host, turnServer.port), Transport.UDP);
                iceAgent.addCandidateHarvester(new TurnCandidateHarvester(ta, new LongTermCredential(turnServer.username, turnServer.password)));
                Log.info("Added TURN harvester: " + ta);
            } catch (Exception ex) {
                Log.warn("Failed to add TURN server: " + turnServer, ex);
            }
        }
    }

    static List<TurnServer> parseTurnServers(String turns) {
        final List<TurnServer> servers = new ArrayList<>();
        if (turns == null) {
            return servers;
        }
        for (String rawEntry : turns.split(",")) {
            final String entry = rawEntry.trim();
            if (!entry.isEmpty()) {
                final Matcher m = TURN_ENTRY.matcher(entry);
                final int port = m.matches() ? Integer.parseInt(m.group(3)) : -1;
                if (port < 1 || port > 65535) {
                    Log.warn("Invalid TURN entry (expected host:port[:user[:pass]] or [ipv6]:port[:user[:pass]]): " + entry);
                    continue;
                }
                String host =  m.group(m.group(1) == null ? 2 : 1);
                servers.add(new TurnServer(host, port, m.group(4), m.group(5)));
            }
        }
        return servers;
    }

    static final class TurnServer {
        final String host;
        final int port;
        final String username;
        final String password;

        TurnServer(String host, int port, String username, String password) {
            this.host = host;
            this.port = port;
            this.username = username;
            this.password = password;
        }

        @Override
        public String toString() {
            return (host.indexOf(':') >= 0 ? "[" + host + "]" : host) + ":" + port;
        }
    }

    // an agent can only run connectivity establishment once: after a session its checklist is completed and the component socket has been closed
    protected boolean isIceAgentReusable() {
        return iceAgent != null && mediaStream != null && !isIceAgentOver();
    }

    protected boolean isIceAgentOver() {
        final IceProcessingState state = iceAgent == null ? null : iceAgent.getState();
        return state != null && state.isOver();
    }

    // stops the ICE agent so that it does not keep harvesting candidates or running connectivity checks
    protected synchronized void freeIceAgent() {
        if (iceAgent != null) {
            Log.info("Stopping ICE agent");
            iceAgent.free();
            iceAgent = null;
            mediaStream = null;
        }
    }

    /** @return true if the latch was released before the timeout or cancellation */
    protected static boolean awaitIceProcessing(CountDownLatch latch, long timeoutMs, BooleanSupplier cancelled) throws IOException {
        final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        try {
            while (!cancelled.getAsBoolean() && System.nanoTime() < deadline) {
                if (latch.await(100L, TimeUnit.MILLISECONDS)) {
                    return true;
                }
            }
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("ICE wait interrupted", e);
        }
    }

    protected SessionTransport openIceSession(boolean compatibilityMode, boolean clientMode) throws IOException {
        if (mediaStream == null) {
            throw new IOException("ICE media stream is not available");
        }
        final Component rtp = mediaStream.getComponent(Component.RTP);
        if (rtp == null || rtp.getSelectedPair() == null) {
            throw new IOException("ICE has no selected RTP pair");
        }
        try {
            return IceSessionTransport.open(rtp, CustomTrustManager.initSslContext(compatibilityMode), clientMode);
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new IOException("Could not initialize the ICE session encryption", e);
        }
    }

    public String getLocalSdpDesc() {
        try {
            return Base64.getEncoder().encodeToString(SdpUtils.createSDPDescription(iceAgent).getBytes(StandardCharsets.UTF_8));
        } catch (SdpException e) {
            Log.error("Failed to create local SDP", e);
            return null;
        }
    }

    // older assistants did not URL-encode the description, so the RVS turned '+' into spaces
    protected static String decodeSdp(String b64) throws IOException {
        try {
            return new String(Base64.getDecoder().decode(b64.replace(' ', '+').trim()), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new IOException("Malformed ICE description", e);
        }
    }

    public static String appendIceParam(String query, String iceInfo) {
        if (iceInfo == null || iceInfo.isEmpty()) {
            return query;
        }
        return query + "&ice=" + URLEncoder.encode(iceInfo, StandardCharsets.UTF_8);
    }
}
