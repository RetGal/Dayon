package mpo.dayon.assisted.network;

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

import static mpo.dayon.common.network.message.NetworkMessageType.CLIPBOARD_FILES;
import static mpo.dayon.common.network.message.NetworkMessageType.PING;
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

    private final Thread receiver; // in

    private NetworkSender sender; // out

    private ObjectInputStream in;

    private final Thread fileReceiver; // file in

    private NetworkSender fileSender; // file out

    private ObjectInputStream fileIn;

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
        }, "CommandReceiver");

        this.fileReceiver = new Thread(new RunnableEx() {
            @Override
            protected void doRun() throws Exception {
                NetworkAssistedEngine.this.fileReceivingLoop();
            }
        }, "FileReceiver");
    }

    @Override
    public void configure(@Nullable NetworkAssistedEngineConfiguration configuration) {
        this.configuration = configuration;
    }

    public void start() throws IOException, NoSuchAlgorithmException, KeyManagementException {
        Log.info("Connecting to [" + configuration.getServerName() + "][" + configuration.getServerPort() + "]...");

        SSLSocket connection = initSocket();
        ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(connection.getOutputStream()));
        sender = new NetworkSender(out); // the active part (!)
        sender.start(1);
        sender.ping();
        in = initInputStream(connection);
        receiver.start();

        SSLSocket fileConnection = initSocket();
        ObjectOutputStream fileOut = new ObjectOutputStream(new BufferedOutputStream(fileConnection.getOutputStream()));
        fileSender = new NetworkSender(fileOut); // the active part (!)
        fileSender.start(1);
        fileSender.ping();
        fileIn = new ObjectInputStream(new BufferedInputStream(fileConnection.getInputStream()));
        fileReceiver.start();
    }

    private SSLSocket initSocket() throws NoSuchAlgorithmException, IOException, KeyManagementException {
        KeyStore keyStore;
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        try {
            keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(NetworkAssistedEngine.class.getResourceAsStream(KEY_STORE_PATH), KEY_STORE_PASS.toCharArray());
            kmf.init(keyStore, KEY_STORE_PASS.toCharArray());
        } catch (KeyStoreException | CertificateException | UnrecoverableKeyException e) {
            Log.error("Fatal, can not init encryption", e);
        }

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), new TrustManager[]{new CustomTrustManager()}, new SecureRandom());

        SSLSocketFactory ssf = sslContext.getSocketFactory();
        return (SSLSocket) ssf.createSocket(configuration.getServerName(), configuration.getServerPort());
    }

    private ObjectInputStream initInputStream(SSLSocket connection) throws IOException {
        try {
            return new ObjectInputStream(new BufferedInputStream(connection.getInputStream()));
        } catch (StreamCorruptedException ex) {
            throw new IOException("version.wrong");
        }
    }

    private void receivingLoop() throws IOException {

        //noinspection InfiniteLoopStatement
        while (true) {

            NetworkMessage.unmarshallMagicNumber(in); // blocking read (!)
            NetworkMessageType type = NetworkMessage.unmarshallEnum(in, NetworkMessageType.class);
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
                    final NetworkMouseControlMessage mouseControlMessage = NetworkMouseControlMessage.unmarshall(in);
                    controlHandler.handleMessage(mouseControlMessage);
                    break;

                case KEY_CONTROL:
                    final NetworkKeyControlMessage keyControlMessage = NetworkKeyControlMessage.unmarshall(in);
                    controlHandler.handleMessage(keyControlMessage);
                    break;

                case CLIPBOARD_REQUEST:
                    clipboardRequestHandler.handleClipboardRequest(this);
                    break;

                case CLIPBOARD_TEXT:
                    final NetworkClipboardTextMessage clipboardTextMessage = NetworkClipboardTextMessage.unmarshall(in);
                    setClipboardContents(clipboardTextMessage.getText(), clipboardOwner);
                    sender.ping();
                    break;

                case PING:
                    break;

                default:
                    throw new IllegalArgumentException(String.format(UNSUPPORTED_TYPE, type));
            }
        }
    }

    private void fileReceivingLoop() throws IOException {

        NetworkClipboardFilesHelper filesHelper = new NetworkClipboardFilesHelper();

        //noinspection InfiniteLoopStatement
        while (true) {

            NetworkMessageType type;
            if (filesHelper.isIdle()) {
                NetworkMessage.unmarshallMagicNumber(fileIn); // blocking read (!)
                type = NetworkMessage.unmarshallEnum(fileIn, NetworkMessageType.class);
                Log.debug("Received " + type.name());
            } else {
                type = NetworkMessageType.CLIPBOARD_FILES;
            }

            if (type.equals(CLIPBOARD_FILES)) {
                final NetworkClipboardFilesMessage clipboardFiles = NetworkClipboardFilesMessage.unmarshall(fileIn, filesHelper);
                filesHelper = handleNetworkClipboardFilesHelper(filesHelper, clipboardFiles, clipboardOwner);
                if (filesHelper.isIdle()) {
                    sender.ping();
                }
            } else if (!type.equals(PING)) {
                throw new IllegalArgumentException(String.format(UNSUPPORTED_TYPE, type));
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

    public void sendClipboardFiles(List<File> files, long size, String basePath) {
        if (fileSender != null) {
            fileSender.sendClipboardContentFiles(files, size, basePath);
        }
    }
}
