package mpo.dayon.assisted.network;

import mpo.dayon.assisted.compressor.CompressorEngineConfiguration;
import mpo.dayon.assisted.compressor.CompressorEngineListener;
import mpo.dayon.assisted.control.NetworkControlMessageHandler;
import mpo.dayon.assisted.mouse.MouseEngineListener;
import mpo.dayon.common.buffer.MemByteBuffer;
import mpo.dayon.common.capture.Capture;
import mpo.dayon.common.concurrent.RunnableEx;
import mpo.dayon.common.configuration.Configurable;
import mpo.dayon.common.error.FatalErrorHandler;
import mpo.dayon.common.event.Listeners;
import mpo.dayon.common.log.Log;
import mpo.dayon.common.network.NetworkEngine;
import mpo.dayon.common.network.message.*;
import mpo.dayon.common.security.CustomTrustManager;
import mpo.dayon.common.squeeze.CompressionMethod;

import javax.net.ssl.*;
import java.awt.*;
import java.awt.datatransfer.ClipboardOwner;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;

import static java.lang.String.format;

public class NetworkAssistedEngine extends NetworkEngine
        implements Configurable<NetworkAssistedEngineConfiguration>, CompressorEngineListener, MouseEngineListener {
    private NetworkAssistedEngineConfiguration configuration;

    private final NetworkCaptureConfigurationMessageHandler captureConfigurationHandler;

    private final NetworkCompressorConfigurationMessageHandler compressorConfigurationHandler;

    private final NetworkControlMessageHandler controlHandler;

    private final NetworkClipboardRequestMessageHandler clipboardRequestHandler;

    private final ClipboardOwner clipboardOwner;

    private final Listeners<NetworkAssistedEngineListener> listeners = new Listeners<>();

    public NetworkAssistedEngine(NetworkCaptureConfigurationMessageHandler captureConfigurationHandler,
                                 NetworkCompressorConfigurationMessageHandler compressorConfigurationHandler,
                                 NetworkControlMessageHandler controlHandler,
                                 NetworkClipboardRequestMessageHandler clipboardRequestHandler, ClipboardOwner clipboardOwner) {
        this.captureConfigurationHandler = captureConfigurationHandler;
        this.compressorConfigurationHandler = compressorConfigurationHandler;
        this.controlHandler = controlHandler;
        this.clipboardRequestHandler = clipboardRequestHandler;
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

    public void addListener(NetworkAssistedEngineListener listener) {
        listeners.add(listener);
    }

    public void connect() {
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
        }
    }

    @SuppressWarnings("java:S2095") // our sockets MUST NOT be closed
    private void start() throws IOException, NoSuchAlgorithmException, KeyManagementException, CertificateEncodingException {
        Log.info(format("Connecting to [%s][%s]...", configuration.getServerName(), configuration.getServerPort()));
        fireOnConnecting(configuration);
        SSLSocketFactory ssf = CustomTrustManager.initSslContext(false).getSocketFactory();
        connection = (SSLSocket) ssf.createSocket();
        connection.setNeedClientAuth(true);
        connection.connect(new InetSocketAddress(configuration.getServerName(), configuration.getServerPort()), 5000);
        initInputStream();

        if (receiver == null) {
            Log.info("Getting the receivers ready");
            runReceivers();
        }
        receiver.start();

        initSender(1);
        // The first message being sent to the assistant (e.g. version identification).
        sender.sendHello();

        fileConnection = (SSLSocket) ssf.createSocket(configuration.getServerName(), configuration.getServerPort());
        initFileSender();
        fileIn = new ObjectInputStream(new BufferedInputStream(fileConnection.getInputStream()));
        fileReceiver.start();
        fireOnConnected(CustomTrustManager.calculateFingerprints(connection.getSession(), this.getClass().getSimpleName()));
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

                switch (type) {
                    case CAPTURE_CONFIGURATION:
                        final NetworkCaptureConfigurationMessage captureConfigurationMessage = NetworkCaptureConfigurationMessage.unmarshall(in);
                        captureConfigurationHandler.handleConfiguration(captureConfigurationMessage);
                        break;

                    case COMPRESSOR_CONFIGURATION:
                        final NetworkCompressorConfigurationMessage compressorConfigurationMessage = NetworkCompressorConfigurationMessage.unmarshall(in);
                        compressorConfigurationHandler.handleConfiguration(compressorConfigurationMessage);
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
                        clipboardRequestHandler.handleClipboardRequest();
                        break;

                    case CLIPBOARD_TEXT:
                        final NetworkClipboardTextMessage clipboardTextMessage = NetworkClipboardTextMessage.unmarshall(in);
                        setClipboardContents(clipboardTextMessage.getText(), clipboardOwner);
                        sender.ping();
                        break;

                    case PING:
                        break;

                    default:
                        throw new IllegalArgumentException(format(UNSUPPORTED_TYPE, type));
                }
            }
        } catch (IOException ex) {
            handleIOException(ex);
        } finally {
            closeConnections();
            fireOnDisconnecting();
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
    public void onCompressed(Capture capture, CompressionMethod compressionMethod, CompressorEngineConfiguration compressionConfiguration,
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

    public void sendResizeScreen(int width, int height) {
        if (sender != null) {
            sender.sendResizeScreen(width, height);
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

}
