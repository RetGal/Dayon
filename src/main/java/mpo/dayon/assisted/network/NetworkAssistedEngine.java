package mpo.dayon.assisted.network;

import mpo.dayon.assistant.network.https.NetworkAssistantHttpsEngine;
import mpo.dayon.assisted.compressor.CompressorEngineConfiguration;
import mpo.dayon.assisted.compressor.CompressorEngineListener;
import mpo.dayon.assisted.control.NetworkControlMessageHandler;
import mpo.dayon.assisted.mouse.MouseEngineListener;
import mpo.dayon.common.buffer.MemByteBuffer;
import mpo.dayon.common.capture.Capture;
import mpo.dayon.common.concurrent.RunnableEx;
import mpo.dayon.common.configuration.Configurable;
import mpo.dayon.common.log.Log;
import mpo.dayon.common.network.NetworkEngine;
import mpo.dayon.common.network.NetworkSender;
import mpo.dayon.common.network.message.*;
import mpo.dayon.common.security.CustomTrustManager;
import mpo.dayon.common.squeeze.CompressionMethod;
import org.jetbrains.annotations.Nullable;

import javax.net.ssl.*;
import java.awt.*;
import java.awt.datatransfer.ClipboardOwner;
import java.io.*;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.List;

import static mpo.dayon.common.security.CustomTrustManager.KEY_STORE_PASS;
import static mpo.dayon.common.security.CustomTrustManager.KEY_STORE_PATH;

public class NetworkAssistedEngine extends NetworkEngine
        implements Configurable<NetworkAssistedEngineConfiguration>, CompressorEngineListener, MouseEngineListener {
    private NetworkAssistedEngineConfiguration configuration;

    private final NetworkCaptureConfigurationMessageHandler captureConfigurationHandler;

    private final NetworkCompressorConfigurationMessageHandler compressorConfigurationHandler;

    private final NetworkControlMessageHandler controlHandler;

    private final NetworkClipboardRequestMessageHandler clipboardRequestHandler;

    private final ClipboardOwner clipboardOwner;

    private final Thread receiver;

    private ObjectInputStream in;

    private NetworkSender sender;

    public NetworkAssistedEngine(NetworkCaptureConfigurationMessageHandler captureConfigurationHandler,
                                 NetworkCompressorConfigurationMessageHandler compressorConfigurationHandler, NetworkControlMessageHandler controlHandler, NetworkClipboardRequestMessageHandler clipboardRequestHandler, ClipboardOwner clipboardOwner) {
        this.captureConfigurationHandler = captureConfigurationHandler;
        this.compressorConfigurationHandler = compressorConfigurationHandler;
        this.controlHandler = controlHandler;
        this.clipboardRequestHandler = clipboardRequestHandler;
        this.clipboardOwner = clipboardOwner;

        this.receiver = new Thread(new RunnableEx() {
            @Override
            protected void doRun() throws Exception {
                NetworkAssistedEngine.this.receivingLoop();
            }
        }, "NetworkReceiver");
    }

    @Override
    public void configure(@Nullable NetworkAssistedEngineConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public void start()
            throws IOException, NoSuchAlgorithmException, KeyManagementException {
        Log.info("Connecting to [" + configuration.getServerName() + "][" + configuration.getServerPort() + "]...");

        SSLSocket connection = initSocket();

        ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(connection.getOutputStream()));

        sender = new NetworkSender(out); // the active part (!)
        sender.start(1);
        sender.ping();

        in = new ObjectInputStream(new BufferedInputStream(connection.getInputStream()));

        receiver.start();
    }

    private SSLSocket initSocket() throws NoSuchAlgorithmException, IOException, KeyManagementException {
        KeyStore keyStore;
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        try {
            keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(NetworkAssistantHttpsEngine.class.getResourceAsStream(KEY_STORE_PATH), KEY_STORE_PASS.toCharArray());
            kmf.init(keyStore, KEY_STORE_PASS.toCharArray());
        } catch (KeyStoreException | CertificateException | UnrecoverableKeyException e) {
            Log.error("Fatal, can not init encryption", e);
        }

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), new TrustManager[]{new CustomTrustManager()}, new SecureRandom());

        SSLSocketFactory ssf = sslContext.getSocketFactory();
        return (SSLSocket) ssf.createSocket(configuration.getServerName(), configuration.getServerPort());
    }

    private void receivingLoop() throws IOException {

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
                case CAPTURE_CONFIGURATION:
                    final NetworkCaptureConfigurationMessage captureConfigurationMessage = NetworkCaptureConfigurationMessage.unmarshall(in);
                    captureConfigurationHandler.handleConfiguration(NetworkAssistedEngine.this, captureConfigurationMessage);
                    break;

                case COMPRESSOR_CONFIGURATION:
                    final NetworkCompressorConfigurationMessage compressorConfigurationMessage = NetworkCompressorConfigurationMessage.unmarshall(in);
                    compressorConfigurationHandler.handleConfiguration(NetworkAssistedEngine.this, compressorConfigurationMessage);
                    break;

                case MOUSE_CONTROL:
                    final NetworkMouseControlMessage mouseControlMessagee = NetworkMouseControlMessage.unmarshall(in);
                    controlHandler.handleMessage(this, mouseControlMessagee);
                    break;

                case KEY_CONTROL:
                    final NetworkKeyControlMessage keyControlMessage = NetworkKeyControlMessage.unmarshall(in);
                    controlHandler.handleMessage(this, keyControlMessage);
                    break;

                case CLIPBOARD_REQUEST:
                    clipboardRequestHandler.handleClipboardRequest(this);
                    break;

                case CLIPBOARD_TEXT:
                    final NetworkClipboardTextMessage clipboardTextMessage = NetworkClipboardTextMessage.unmarshall(in);
                    setClipboardContents(clipboardTextMessage.getText(), clipboardOwner);
                    sender.ping();
                    break;

                case CLIPBOARD_FILES:
                    final NetworkClipboardFilesMessage clipboardFiles = NetworkClipboardFilesMessage.unmarshall(in, filesHelper);
                    filesHelper = handleNetworkClipboardFilesHelper(filesHelper, clipboardFiles, clipboardOwner);
                    if (filesHelper.isIdle()) {
                        sender.ping();
                    }
                    break;

                case PING:
                    break;

                default:
                    throw new IllegalArgumentException("Unsupported message type [" + type + "]!");
            }
        }
    }

    /**
     * The first message being sent to the assistant (e.g., version
     * identification).
     */
    public void sendHello() {
        if (sender != null) {
            sender.sendHello();
        }
    }

    /**
     * May block (!)
     * <p/>
     * We're receiving a fully compressed (and ready to send over the network)
     * capture.
     */
    @Override
    public void onCompressed(Capture capture, CompressionMethod compressionMethod, @Nullable CompressorEngineConfiguration compressionConfiguration,
                             MemByteBuffer compressed) {
        if (sender != null) {
            sender.sendCapture(capture, compressionMethod, compressionConfiguration, compressed);
        }
    }

    /**
     * May block (!)
     */
    @Override
    public boolean onLocationUpdated(Point location) {
        return sender == null || sender.sendMouseLocation(location);
    }

    public void sendClipboardText(String text, int size) {
        if (sender != null) {
            sender.sendClipboardContentText(text, size);
        }

    }

    public void sendClipboardFiles(List<File> files, long size) {
        if (sender != null) {
            sender.sendClipboardContentFiles(files, size);
        }

    }
}
