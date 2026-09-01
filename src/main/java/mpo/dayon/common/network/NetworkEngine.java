package mpo.dayon.common.network;

import com.dosse.upnp.UPnP;
import mpo.dayon.common.log.Log;
import mpo.dayon.common.network.message.*;
import org.ice4j.Transport;
import org.ice4j.TransportAddress;
import org.ice4j.ice.Agent;
import org.ice4j.ice.harvest.StunCandidateHarvester;
import org.ice4j.ice.harvest.TurnCandidateHarvester;
import org.ice4j.security.LongTermCredential;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

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

    private static final String CLIPBOARD_DEBUG = "setClipboardContents %s";

    private static final String WHATSMYIP_SERVER_URL = "https://fensterkitt.ch/dayon/whatismyip.php";

    protected NetworkSender sender; // out

    private NetworkSender fileSender; // file out

    protected Thread receiver; // in

    protected ObjectInputStream in;

    protected Thread fileReceiver; // file in

    protected ObjectInputStream fileIn;

    protected SSLServerSocket server;

    protected SSLSocket connection;

    protected SSLSocket fileConnection;

    protected Agent iceAgent;

    protected final AtomicBoolean cancelling = new AtomicBoolean(false);

    private final Object upnpEnabledLOCK = new Object();

    private Boolean upnpEnabled;

    public static AtomicReference<Boolean> isOwnPortAccessible = new AtomicReference<>();

    private String localAddress = null;

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
     */
    public void sendClipboardFiles(List<File> files, long size, String basePath) {
        if (fileSender != null) {
            fileSender.sendClipboardContentFiles(files, size, basePath);
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

    protected void handleIncomingClipboardFiles(ObjectInputStream fileIn, ClipboardOwner clipboardOwner) throws IOException {
        String tmpDir = getTempDir();
        NetworkClipboardFilesHelper filesHelper = new NetworkClipboardFilesHelper();

        //noinspection InfiniteLoopStatement
        while (true) {
            NetworkMessageType type;
            if (filesHelper.isDone()) {
                NetworkMessage.unmarshallMagicNumber(fileIn); // blocking read (!)
                type = NetworkMessage.unmarshallEnum(fileIn, NetworkMessageType.class);
                Log.debug("Received " + type.name());
                if (!type.equals(CLIPBOARD_FILES) && !type.equals(PING)) {
                    throw new IllegalArgumentException(format(UNSUPPORTED_TYPE, type));
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
        }
    }

    protected void fireOnClipboardReceived() {
    }

    /**
     * closes all connections AND resets the cancelling flag
     */
    protected void closeConnections() {
        if (sender != null) {
            sender.cancel();
        }
        receiver = safeInterrupt(receiver);
        safeClose(in, connection, server);

        if (fileSender != null) {
            fileSender.cancel();
        }
        fileReceiver = safeInterrupt(fileReceiver);
        safeClose(fileIn, fileConnection);
        cancelling.set(false);
    }

    protected void createInputStream() throws IOException {
        if (connection == null) {
            throw new IOException("Connection not established");
        }
        try {
            in = new ObjectInputStream(new BufferedInputStream(connection.getInputStream()));
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

    private boolean isPortAccessible(String publicIp, int portNumber) throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(publicIp, portNumber), 1000);
        return true;
    }

    // more reliable, but currently defunct until a solution with the hoster is found
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

    // creates unrestricted port forwarding
    public boolean selfTest(String publicIp, int portNumber) {
        return selfTest(publicIp, portNumber, null);
    }

    // creates port forwarding for the specific remote host only
    protected boolean selfTest(String publicIp, int portNumber, String remoteHost) {
        if (publicIp == null) {
            isOwnPortAccessible.set(false);
            return false;
        }
        if (!manageRouterPorts(0, portNumber, remoteHost)) {
            boolean accessible;
            try (ServerSocket ignored = new ServerSocket(portNumber)) {
                accessible = isPortAccessible(publicIp, portNumber);
            } catch (IOException e) {
                accessible = false;
            }
            if (!accessible) {
                Log.warn("Port " + portNumber + " is not reachable from the outside");
                isOwnPortAccessible.set(false);
                localAddress = obtainLocalAddress();
                return false;
            }
        }
        Log.debug("Port " + portNumber + " is reachable from the outside");
        isOwnPortAccessible.set(true);
        return true;
    }

    private String obtainLocalAddress() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("fensterkitt.ch", 80), 5000);
            return socket.getLocalAddress().getHostAddress();
        } catch (IOException e) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("info.cern.ch", 80), 5000);
                return socket.getLocalAddress().getHostAddress();
            } catch (IOException ex) {
                Log.warn("No internet connection");
                return "0";
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
                isOwnPortAccessible.set(true);
                return true;
            }
            Log.warn(format("Failed to enable forwarding for port %d", newPort));
            isOwnPortAccessible.set(false);
            return false;
        }
        isOwnPortAccessible.set(true);
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

    protected void initializeIceAgent() {
        if (iceAgent == null) {
            Log.debug("Initializing new ICE agent");
            iceAgent = new Agent();
            iceAgent.setLoggingLevel(Level.FINEST);
            Log.debug("Number of STUN harvesters: " + iceAgent.getHarvesters().size());
            String[] stunServers = {
                    "jitsi.org:3478",
                    "stun.fbsbx.com:3478",
                    "stun.l.google.com:19302",
                    "stun.cloudflare.com:3478"
            };
            for (String sts : stunServers) {
                try {
                    String[] parts = sts.split(":");
                    TransportAddress ta = new TransportAddress(new InetSocketAddress(parts[0], Integer.parseInt(parts[1])), Transport.UDP);
                    iceAgent.addCandidateHarvester(new StunCandidateHarvester(ta));
                    Log.debug("Added STUN harvester: " + sts);
                } catch (Exception e) {
                    Log.warn("Failed to add STUN harvester: " + sts, e);
                }
            }
            Log.debug("Number of STUN harvesters: " + iceAgent.getHarvesters().size());
            addTurnServers();
        }
    }

    private void addTurnServers() {
        // optional: read TURN servers from preferences (format: host:port[:username:password],comma-separated)
        final String turns = mpo.dayon.common.preference.Preferences.getPreferences().getStringPreference("ice.turn.servers", "");
        if (turns.isEmpty()) {
            return;
        }
        Log.info("Configuring TURN servers from preferences");
        String[] entries = turns.split(",");
        for (String entry : entries) {
            try {
                String[] parts = entry.split(":");
                if (parts.length >= 2) {
                    String host = parts[0];
                    int port = Integer.parseInt(parts[1]);
                    String user = parts.length > 2 ? parts[2] : null;
                    String pass = parts.length > 3 ? parts[3] : null;
                    TransportAddress ta = new TransportAddress(new InetSocketAddress(host, port), Transport.UDP);
                    LongTermCredential credential = new LongTermCredential(user, pass);
                    iceAgent.addCandidateHarvester(new TurnCandidateHarvester(ta, credential));
                    Log.info("Added TURN harvester: " + host + ":" + port);
                }
            } catch (Exception ex) {
                Log.warn("Failed to parse/add TURN entry: " + entry, ex);
            }
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
}
